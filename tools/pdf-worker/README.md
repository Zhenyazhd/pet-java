# pdf-worker

Stateless HTTP service that compiles a [Typst](https://typst.app/) document to PDF, optionally searching the layout
scale so the result fits exactly one page. Design: [SPEC.md](SPEC.md), build order: [PLAN.md](PLAN.md).


## Run natively

Requires Java 25 and `typst` >= 0.13 (`brew install typst`; the Docker image pins 0.15.1).

```bash
./mvnw package
WORKER_TOKEN=dev java -jar target/pdf-worker.jar
```

## Run in Docker

```bash
docker build -t pdf-worker .
docker run --rm --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges \
  -e WORKER_TOKEN=dev -p 127.0.0.1:8090:8090 pdf-worker
```

In production also pass `--network none` (or an `internal: true` network shared only with core-api).
Typst needs nothing writable except the per-request temp directory under `/tmp`.

With the repo's `docker-compose.yml` the worker starts together with Postgres (`npm run infra:up`), with the token
taken from `PDF_WORKER_TOKEN` in the root `.env`. Compose does not rebuild the image on its own — after changing
the worker run `docker compose build pdf-worker`.

## Configuration

| Variable | Default | |
|---|---|---|
| `WORKER_TOKEN` | — (required) | shared secret, sent by callers as `X-Worker-Token` |
| `WORKER_PORT` | `8090` | |
| `WORKER_CONCURRENCY` | CPU count | compilations at once; extra requests get `503` |
| `COMPILE_TIMEOUT_SECONDS` | `60` | one deadline for the whole request, including every compile of a fit search |
| `MAX_SOURCE_BYTES` | `1048576` | larger request bodies get `413` |
| `MAX_PDF_BYTES` | `10485760` | larger PDFs get `422` |
| `TYPST_BIN` | `typst` | |

## API

`POST /compile` takes `{"source": "<Typst source>", "fit": <optional boolean, default false>}`.

```bash
# 200 application/pdf
curl -H 'X-Worker-Token: dev' -d '{"source":"Hello, Привет"}' -o out.pdf localhost:8090/compile

# fit mode: also returns X-Page-Scale
curl -D - -H 'X-Worker-Token: dev' -d '{"source":"...","fit":true}' -o out.pdf localhost:8090/compile

# 422 compile_error, the message holds the end of the Typst diagnostics
curl -H 'X-Worker-Token: dev' -d '{"source":"#oops"}' localhost:8090/compile

curl localhost:8090/healthz   # 200 while the process is up
curl localhost:8090/readyz    # 200 once the warm-up compile succeeded, 503 before
```

| Status | `error` | Meaning |
|---|---|---|
| 200 | — | PDF in the body, `X-Compile-Millis`, `X-Page-Count` / `X-Page-Fill` (see below) and, in fit mode, `X-Page-Scale` |
| 400 | `bad_request` | body is not `{"source": "..."}` or the source is blank |
| 401 | `unauthorized` | missing or wrong `X-Worker-Token` |
| 405 | `method_not_allowed` | not a `POST` |
| 413 | `too_large` | body over `MAX_SOURCE_BYTES` |
| 422 | `compile_error` | the document does not compile (or the PDF is over `MAX_PDF_BYTES`); do not retry |
| 503 | `busy` | every slot is in use; retry after `Retry-After` seconds |
| 504 | `timeout` | compilation took longer than `COMPILE_TIMEOUT_SECONDS` |
| 500 | `internal` | worker failure; safe to retry |

The compile runs `typst compile --root <tempdir> --ignore-system-fonts main.typ main.pdf` in a throwaway directory that
holds only `main.typ`: no system fonts (only the fonts embedded in Typst, e.g. Libertinus Serif), no
access to other files. Packages are not provided: an `@preview` import would be downloaded, which the network policy
(no egress) blocks, and the child environment carries a dead `HTTPS_PROXY` so the download fails everywhere else too.
The child also runs with a cleared environment, a file size limit derived from `MAX_PDF_BYTES` (`ulimit -f`) and, on
Linux, a 512 MiB address space limit (`ulimit -v`). A compile killed by the file size limit is `422` ("PDF is larger
than"), a Typst out-of-memory abort is `422`, and any other fatal signal (OOM killer, crash) is a retryable `500`.

## Page metrics

A successful compile reports `X-Page-Count` (number of pages) and `X-Page-Fill` (0.00–1.00): how full the **last**
page is, measured from the top of its first line to the baseline of its last line against the text area. The text area
is the page minus a margin the size of the one above the first line, so the document must not print page numbers or
other footer text (they would count as text) and should have roughly equal top and bottom margins. A one-page resume
that is full reads about `0.95–0.99`; the headers are left out if the PDF could not be measured.

## Fit mode

With `"fit": true` the template is compiled several times with `--input scale=<x>`; it reads the value through
`sys.inputs.at("scale", default: "1.0")` and scales font size, leading and spacing. Bounds are `0.88` to `1.12`,
the target is one page with fill >= 0.93.

1. Compile at `1.0`. One page and fill >= 0.93: done (`X-Page-Scale: 1.000`, a single compile).
2. Otherwise try the far bound (`0.88` after an overflow, `1.12` after a short page). If it still does not give the
   wanted result (two pages at `0.88`, or fill < 0.93 at `1.12`), that compile is returned as is.
3. Otherwise bisect for the largest scale that still gives exactly one page, until the bracket is below `0.005` or
   7 compiles were made.

The response carries the PDF, metrics and `X-Page-Scale` (three decimals, the precision of the search) of the best compile: a one-page result beats
any overflow, among one-page results the largest scale wins. All compiles share the `COMPILE_TIMEOUT_SECONDS` budget; on
expiry the best result so far is returned (`504` only if nothing compiled). A compile error at any scale is `422`.
Without `fit` there is a single compile, no scale input and no `X-Page-Scale`.

## Tests

`./mvnw test`. Tests that need `typst` are skipped when it is not installed.

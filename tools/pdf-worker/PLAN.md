# pdf-worker — implementation plan

The worker compiles Typst.

Step-by-step plan for building the worker described in [SPEC.md](SPEC.md). Every stage ends with something
you can check with `curl` or a test — no `core-api` changes are needed until integration.

## Stage 0. Prerequisites

- Install `typst` (`brew install typst`, >= 0.13) and Java 25.

**Done when:** `typst compile --ignore-system-fonts x.typ` produces a PDF locally.

## Stage 1. Skeleton and `/healthz`

- `pom.xml`: Java 25, jar with `Main-Class`, JUnit Jupiter tests.
- Copy the Maven wrapper (`mvnw`, `.mvn/wrapper`) from `core-api` (copy only, `core-api` stays untouched).
- `WorkerMain`: `HttpServer` on `WORKER_PORT`, executor `Executors.newVirtualThreadPerTaskExecutor()`,
  `GET /healthz`. A shutdown hook calls `server.stop(...)` so in-flight requests finish.

**Done when:** `java -jar target/pdf-worker.jar` runs and `curl localhost:8090/healthz` returns `{"status":"ok"}`.

## Stage 2. `WorkerConfig`

- A `record` with every environment variable from SPEC §3.4 and its default.
- Startup validation: a blank `WORKER_TOKEN` or a non-positive limit stops the process with a clear error.

**Done when:** unit tests for parsing and for "no token" pass.

## Stage 3. `Compiler` — the core, no HTTP yet

The main stage.

- Temp directory, `main.typ`, run `typst compile --root <dir> --ignore-system-fonts [--input scale=<x>] main.typ main.pdf`.
  Process output goes to a file, not a pipe.
- `waitFor(timeout)`. On timeout kill `process.descendants()` first, then the process itself.
- Check the PDF exists and fits the size limit, trim the log, delete the directory in `finally`.
- Result: `sealed interface CompileResult` with `Ok(pdf, millis)`, `Failed(log)`, `TimedOut`, so the handler
  only maps a variant to an HTTP status.
- Fit mode (`fit: true`): search the `scale` input between 0.88 and 1.12 for the largest scale that gives one page
  (see SPEC §3.3), at most 7 compiles, one shared deadline. `CompileResult.Ok` carries the scale (null outside fit mode).
- Do not port the Docker fallback.

Also `src/main/resources/warmup.typ`: a tiny document using the embedded font with an accented and a Cyrillic letter.

**Done when:** tests pass for a successful build of `warmup.typ` (PDF starts with `%PDF`), a syntax error
(`Failed` with a log), a very long loop (`TimedOut`, and no `typst` process left behind), and the fit cases (short text
scales up, long text scales down, hopeless text stays at two pages, fitting text needs one compile).
The temp directory is always removed. Tests that need `typst` use `Assumptions.assumeTrue(...)` so they are
skipped, not failed, on machines without it.

## Stage 4. `CompileHandler` — `POST /compile`

Checks in order, each cheaper than the next:

1. Method is not `POST` → `405`.
2. `X-Worker-Token` compared with `MessageDigest.isEqual`, otherwise `401`.
3. Read at most `MAX_SOURCE_BYTES + 1` bytes; over the limit → `413`. Never buffer the whole body before checking size.
4. JSON → `source` (and optional `fit`); blank or invalid → `400`.
5. `semaphore.tryAcquire()`; no free slot → `503` with `Retry-After: 1`.
6. Run `Compiler` and map: `Ok` → `200` with the PDF, `X-Compile-Millis`, `X-Page-Count`, `X-Page-Fill` (+ `X-Page-Scale` in fit mode), `Failed` → `422`,
   `TimedOut` → `504`, exception → `500`.
7. `release()` in `finally`.

JSON parsing uses Jackson (the only runtime dependency), packaged into one jar with `maven-shade-plugin`.

Logs are `key=value`: sizes, durations, status. Never log the Typst source.

**Done when:** integration tests start the server on port 0 and check every status code from SPEC §3.2 through
`java.net.http.HttpClient`. For `503`, use `WORKER_CONCURRENCY=1` and two requests with a slow document.

## Stage 5. `/readyz`

After the server starts, compile `warmup.typ` (this also proves `typst` runs and the embedded font renders accents and
Cyrillic). Until that passes, `/readyz` answers `503`, so a broken pod gets no traffic.

**Done when:** `/readyz` returns `503` before the warm-up compile succeeded and `200` after.

## Stage 6. Docker image

- Build stage: `eclipse-temurin:25-jdk` and `./mvnw package`.
- Runtime image: JRE 25 plus a pinned static `typst` build (v0.15.1, musl) from GitHub releases, `USER 10001`,
  `ENTRYPOINT java -jar`. No cache and no warm-up step.

**The key sandbox check:**

```bash
docker run --rm --read-only --tmpfs /tmp --network none \
  --cap-drop ALL --security-opt no-new-privileges \
  -e WORKER_TOKEN=dev -p 127.0.0.1:8090:8090 pdf-worker
```

Then `curl` a document. If `typst` tries to write somewhere, `--read-only` exposes it here rather than in production.

**Done when:** `curl` gets a PDF from a container with no network and a read-only filesystem.

## Stage 7. Local run and README

- `README.md`: running natively and in Docker, environment variables, `curl` examples for every status code.
- Do not add the service to `docker-compose.yml` yet — that is an integration step. Note that ports on an
  `internal: true` network cannot be published, so local development binds the port to `127.0.0.1` instead.

## Order and effort

Stages 1–2 take about an hour, stage 3 is the largest and most important, stages 4–5 are medium,
and stage 6 is short now that there is no cache to bake.

If time is short, do stages 3 and 6 first: they hold the real risk (timeouts, stuck processes, the sandbox).
The rest is ordinary HTTP plumbing.

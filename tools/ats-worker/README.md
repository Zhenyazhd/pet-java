# ats-worker

Stateless HTTP service that answers one question about a resume PDF: **can an ATS read it?** It extracts the
text layer with [Apache PDFBox](https://pdfbox.apache.org/) the way a parser would and checks what survived.
`POST /check` uses no LLM and needs no network access; nothing is stored or logged (only verdict, score, sizes
and timing). The optional `POST /score` (below) calls an LLM and therefore needs outbound access to it.

It does not score the match with a job posting, and it does not simulate a particular ATS vendor: those are
closed products. It tests what every parser needs — a complete, ordered, plain text layer with standard headings.

## Run natively

Requires Java 25.

```bash
./mvnw package
WORKER_TOKEN=dev java -jar target/ats-worker.jar      # listens on :8091
```

## Run in Docker

```bash
docker build -t ats-worker .
docker run --rm --read-only --tmpfs /tmp --cap-drop ALL --security-opt no-new-privileges \
  -e WORKER_TOKEN=dev -p 127.0.0.1:8091:8091 ats-worker
```

In production also pass `--network none` (or an `internal: true` network shared only with core-api).
Wired into `docker-compose.yml` (service `ats-worker`) and called by core-api; not yet in the Kubernetes manifests.

## API

`POST /check` with header `X-Worker-Token`:

```json
{ "pdfBase64": "<the PDF, base64>", "expected": "<plain text the PDF was built from, optional>" }
```

`expected` is core-api's `ResumePlainTextRenderer` output. With it the worker compares what the PDF says with
what it should say (coverage, order, headings, contacts); without it only the reference-free checks run.

Response `200`:

| Field | |
|---|---|
| `verdict` | `ok`, `warnings` or `issues` (at least one error) |
| `score` | 0–100, see below |
| `issues[]` | `{code, severity: error｜warning｜info, message}` — the part to show the user |
| `coverage` | share of the expected words found in the PDF text, `null` without `expected` |
| `orderScore` | share of the reading sequence kept, `null` without `expected` |
| `lostLines[]` | expected lines that are mostly missing (max 10) |
| `sectionsFound[]`, `sectionsMissing[]` | recognised headings: summary, experience, education, skills, … |
| `contacts` | `email`, `phone`, `url` readable; `nameAtTop` |
| `pages`, `textChars` | |
| `text`, `textTruncated` | what an ATS sees (first 20 000 characters) |

Errors: `{"error": code, "message": text}` — `400 bad_request`, `401 unauthorized`, `413 too_large`,
`422` (`not_a_pdf`, `unreadable_pdf`, `encrypted`, `too_many_pages`, `too_much_text`, `page_too_large`),
`503 busy` (retry; all slots taken), `504 timeout` (the model) or `504 check_timeout` (reading the PDF). `GET /healthz` returns `200`; `GET /readyz` returns `200`, or
`503 draining` once the worker has been told to stop, so a load balancer stops sending it requests.

## Scoring with an LLM (optional)

`POST /score` asks a model how an ATS would score the resume on six platforms (Workday, Taleo, iCIMS, Greenhouse,
Lever, SuccessFactors) in one request. It is the same call ATS Screener makes, and the prompt is theirs: see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) (MIT, Copyright (c) 2026 Sunny Patel;
[source](https://github.com/sunnypatell/ats-screener/blob/main/src/lib/engine/llm/prompts.ts)). The text is
`src/main/resources/prompts/full-score.md`; the lines that change with and without a vacancy are in
`full-score.targeted.properties` and `full-score.general.properties`.

```json
{ "pdfBase64": "<the resume PDF, base64>", "jobDescription": "<vacancy text, optional>" }
```

The model is given the text an ATS reads from the PDF (extracted exactly as `/check` does), so the score reflects what
survives parsing, not the text the resume was built from. `"resumeText": "<plain text>"` may be sent instead of the PDF.
A PDF without a text layer gets `422 no_text_layer` and the model is not called.

Response `200`: the model's JSON as ATS Screener returns it — `results[6]`, each with `system`, `vendor`,
`overallScore`, `passesFilter`, `breakdown` (formatting, keywordMatch, sections, experience, education) and
`suggestions` — plus `_model` and `_resumeSource` (`pdf` or `text`). The resume is cut to 6000 characters and the vacancy to 4000, as in ATS Screener.
Errors: `503 not_configured` (no API key), `503 busy`, `422` (`no_text_layer`, `not_a_pdf`, `unreadable_pdf`, ...), `502` (`llm_error`, `llm_unreachable`, `bad_model_answer`:
fewer than six results or no JSON), `504 timeout`, plus the usual `400`, `401`, `413`.

What the scores are: one model's estimate from descriptions of the platforms written into the prompt, not
measurements of those products, and they vary between runs. Use the missing keywords and suggestions, not the exact
numbers. Unlike `/check`, resume and vacancy text leave the worker for the model provider, so a worker that has
this enabled cannot run with `--network none`.

## Checks

| Code | Severity | Fires when | Points |
|---|---|---|---|
| `no_text_layer` | error | under 50 characters of text (a scan or an image) | score 0 |
| `garbled_text` | error | more than 1% replacement or control characters | −40 |
| `text_lost` / `text_partly_lost` | error / warning | coverage under 85% / under 95% | −(1−coverage)·60 |
| `reading_order` | error / warning | order under 70% / under 90%, in the worse of stream and position-sorted order | −(1−order)·20 |
| `section_not_recognised` | error | a heading present in `expected` is not recognised in the PDF (e.g. "My Journey") | −10 each |
| `section_missing` | warning | no `expected`, and experience / education / skills heading not found | −5 each |
| `email_not_found`, `phone_not_found`, `url_not_found` | error | in `expected`, not readable in the PDF | −10 each |
| `name_not_at_top` | warning | the first expected line is not in the first 8 text lines | −5 |
| `columns` | warning | a vertical strip ≥ 5 pt that almost no text row crosses, with ≥ 15% of the text on each side, on a page with at least 12 text rows (LaTeX's default 10 pt column gap measures ~7 pt between glyphs) | −25 |
| `images` | info | an image larger than 50×50 px (ATS ignores images) | 0 |
| `long` | info | more than 2 pages | 0 |

How the comparison is made: words are lowercased, NFKC-folded (ligatures) and split on anything that is not a
letter or digit. Coverage counts each word once and also accepts a word found inside the text with all spacing
removed, so letter-spaced headings, kerning-split words and line-end hyphenation do not count as lost. Headings
match in English, French and Russian, ignoring case and spacing. The score is a convenience; the issue list is
the result.

Safety limits: the body (`MAX_REQUEST_BYTES`), pages, page size, 300 000 characters of text, a deadline polled while
the text is read, a bounded regex for contacts, a 2 MB cap on the model's answer, and a whole-call timeout on the model
(a provider that sends headers and then drips the body is cut off). The JDK server gets `sun.net.httpserver.maxReqTime`
(60 s), `maxRspTime` (check + model timeout + 30 s) and `idleInterval` (30 s) at startup, unless `-D` sets them. What
is *not* bounded in time is PDFBox's own decoding of a stream: a PDF whose streams inflate to far more than a resume
should is stopped by the scratch space (the 128 MB `/tmp` tmpfs in compose), the 512 MB heap and
`-XX:+ExitOnOutOfMemoryError` (the container restarts), not by the deadline. The PDFs it receives come from core-api's own
tectonic build of a resume, not from uploads, which is why that is accepted; if arbitrary PDFs were ever sent here,
inflate and cap every stream before PDFBox sees it.

Known limits: the `columns` check needs at least 20 text rows on a page, so it says nothing about a very short page, and a
short resume with a table or a two-block header can still look like columns; the worker cannot tell what a specific vendor does with a layout; `expected` must be the text the
template really renders (a field the template drops shows up as a lost line; headings in `expected` are
English while the PDF may be French — they are compared as sections, not as words); URLs that live only in a
link annotation are not read.

## Configuration

| Variable | Default | |
|---|---|---|
| `WORKER_TOKEN` | — (required) | shared secret, sent as `X-Worker-Token` |
| `WORKER_PORT` | `8091` | |
| `WORKER_CONCURRENCY` | CPU count | checks at once; extra requests get `503` |
| `CHECK_TIMEOUT_SECONDS` | `20` | extraction stops itself at this deadline |
| `MAX_REQUEST_BYTES` | `16777216` | JSON body, base64 included; larger gets `413` |
| `MAX_PAGES` | `10` | pages wider or taller than 5 000 pt are refused (`page_too_large`) |
| `SHUTDOWN_GRACE_SECONDS` | `25` | on SIGTERM `/readyz` turns `503` for 5 s, then the worker waits up to this long for requests in flight and exits as soon as there are none. The platform must wait longer than 5 s + this (compose: `stop_grace_period: 45s`; Kubernetes: `terminationGracePeriodSeconds`) |
| `OPENROUTER_API_KEY` | — | enables `POST /score`; without it that endpoint answers `503` |
| `OPENROUTER_BASE_URL` | `https://openrouter.ai/api/v1` | any OpenAI-compatible endpoint |
| `SCORE_MODEL` | `openai/gpt-4o-mini` | needs JSON mode and a context of at least ~10 000 tokens |
| `SCORE_TIMEOUT_SECONDS` | `90` | |
| `SCORE_CONCURRENCY` | `4` | model calls at once; extra requests get `503` |
| `SCORE_MAX_TOKENS` | `6000` | the answer is about 2 000–4 000 tokens |

## Tests

`./mvnw test` — unit tests build small PDFs with PDFBox (single column, two columns, no text, many pages, hostile
ones); `RealPdfTest` runs the checks on PDFs typeset by tectonic with the production template
(`src/test/resources/pdfs`, see its README); `/score` is tested against a fake model server.

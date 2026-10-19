# PDF worker (pdf-worker) and compile queue — specification

This document describes **what** we are building and **the rules** for writing the code that moves resume PDF compilation out of `core-api` into a separate isolated service, `tools/pdf-worker`, and adds a compile-job queue in `core-api`.

How binding the rules are: **MUST / MUST NOT** — do not violate; **SHOULD** — depart only with an explanation in the PR.

---

## 1. Goals and boundaries

**Goals**

1. Compilation does not load the CPU of `core-api` instances; workers scale independently.
2. Compilation of user input is isolated: the worker has no access to the database, secrets, or the internet.
3. Recompiling an unchanged resume is skipped (cache keyed by a hash of the source).
4. The user's HTTP request does not hang for tens of seconds: the API accepts a job and responds immediately, and the frontend polls for status.
5. A worker or `core-api` instance crashing does not lose jobs.

**Out of scope**

- Changing the engine (Typst and similar) or the resume template.
- Message brokers (Kafka, RabbitMQ, Redis). The queue is a Postgres table.
- Push notifications (SSE/WebSocket) — polling is enough.

---

## 2. Architecture

```
 frontend                core-api (N instances)                      pdf-worker (M pods)
 ────────                ──────────────────────                      ─────────────────────
 POST /api/resume/compile ─▶ CompileJobService.enqueue()
                              ├─ render Typst + sha256
                              ├─ hit in resume_pdf_cache → job DONE immediately
                              └─ miss → INSERT compile_job (QUEUED)
                                         │
                             CompileDispatcher (in every instance)
                              ├─ claim: FOR UPDATE SKIP LOCKED ──────┐
                              ├─ HTTP POST /compile (outside a txn) ──┼──▶ semaphore → typst → PDF
                              └─ store PDF in cache, job DONE ◀───────┘
 GET  /api/resume/compile/{id}      ─▶ status
 GET  /api/resume/compile/{id}/pdf  ─▶ PDF from resume_pdf_cache
```

**Key decision: `core-api` drains the queue, not the worker (push model).**
The worker is a pure `Typst → PDF` function over HTTP. It does **not** talk to Postgres, so it needs no database credentials, and even a fully compromised `typst` process cannot read other users' data. If workers pulled jobs from the table themselves (pull model), that isolation would have to be weakened.

---

## 3. pdf-worker (`tools/pdf-worker`)

### 3.1 Stack and layout

- **Java 25, no Spring.** The HTTP server is `com.sun.net.httpserver.HttpServer` with an executor on virtual threads. Reason: a small attack surface, a fast start.
- Build with Maven + `mvnw`, a single executable jar with no external dependencies except a logger (`slf4j-simple` or `java.util.logging`).
- Package `com.jobsearch.pdfworker`.

```
tools/pdf-worker/
├── SPEC.md, PLAN.md, README.md
├── Dockerfile, .dockerignore
├── mvnw, mvnw.cmd, .mvn/, pom.xml
└── src/
    ├── main/java/com/jobsearch/pdfworker/
    │   ├── WorkerMain.java          reads config, starts the server, /healthz and /readyz, warm-up
    │   ├── WorkerConfig.java        record + parsing env
    │   ├── CompileHandler.java      POST /compile
    │   ├── Compiler.java            runs typst in a temp directory, fit search
    │   ├── PageMetrics.java         page count and fill of a PDF (PDFBox)
    │   ├── CompileResult.java       Ok / Failed / TimedOut
    │   └── Responses.java           JSON and error responses
    ├── main/resources/warmup.typ    tiny document that exercises the embedded font (readyz)
    └── test/java/...
```

### 3.2 Worker HTTP API

Every error response is JSON `{ "error": "<code>", "message": "<text>" }`.

| Method | Path | Request | Success | Errors |
|---|---|---|---|---|
| POST | `/compile` | `Content-Type: application/json`, `{ "source": "<Typst>", "fit": false }` (`fit` optional, default `false`), header `X-Worker-Token` | `200 application/pdf`, body is the PDF, headers `X-Compile-Millis` (all compiles together), `X-Page-Count` and `X-Page-Fill` (left out when the PDF cannot be measured) and, in fit mode only, `X-Page-Scale` (three decimals, the precision of the search) | `400 bad_request` — missing/empty `source`, invalid JSON; `401 unauthorized` — bad token; `413 too_large` — source over the limit; `422 compile_error` — Typst failed to compile, `message` is the tail of the log; `503 busy` + `Retry-After: 1` — no free slots; `504 timeout` — compile time exceeded; `500 internal` — anything else |
| GET | `/healthz` | — | `200 {"status":"ok"}` | — |
| GET | `/readyz` | — | `200` once the warm-up compile of `warmup.typ` succeeded | `503` |

Rules:

- `422` is a **permanent** error (a problem in the document); retrying is pointless. `503`, `504`, `500`, and network errors are **transient**.
- Compare the token in constant time (`MessageDigest.isEqual`).
- MUST NOT log the contents of `source` or the PDF — only sizes, duration, and the result code.

### 3.3 Compilation rules

1. Concurrency is bounded by `Semaphore(WORKER_CONCURRENCY)`, defaulting to the number of cores. Acquire the permit with `tryAcquire()` **without waiting**: no slot → immediately `503 busy`. The queue lives in `core-api`, not in the worker's memory.
2. Release the permit in `finally`.
3. Each request gets its own temp directory (`Files.createTempDirectory`), deleted in `finally` on every outcome.
4. The command is: `typst compile --root <workdir> --ignore-system-fonts [--input scale=<x>] main.typ main.pdf`, run in the temp directory, which holds only `main.typ`.
   - `--root` confines file access of the document to that directory; `--ignore-system-fonts` leaves only the fonts embedded in the binary (Libertinus Serif: Latin, Cyrillic, Greek);
   - packages are not provided: an `@preview` import would download third-party code, which the network policy (no egress) blocks; the child environment also carries a dead `HTTPS_PROXY`/`HTTP_PROXY`/`ALL_PROXY` (`http://127.0.0.1:9`) so the download fails everywhere else too;
   - the child environment is cleared: only `PATH`, `HOME`/`XDG_CACHE_HOME` (the temp directory) and the proxy variables are set;
   - typst is started through `sh -c 'ulimit -f <blocks>; ulimit -v 524288; exec typst ...'`: the file size limit follows `MAX_PDF_BYTES` (SIGXFSZ -> `422` "PDF is larger than"), the address space limit applies on Linux only (a Typst allocation-failure abort, SIGABRT -> `422`); any other signal exit (OOM kill, crash) is a worker error, `500 internal`, retryable.
5. Send process output to a file, not a pipe (reading a pipe blocks until the process exits and breaks the timeout).
6. Timeout `COMPILE_TIMEOUT_SECONDS` (default 60), one deadline for the whole request (all compiles of a fit search). When it fires, kill the process **tree**: `process.descendants().forEach(ProcessHandle::destroyForcibly)`, then `process.destroyForcibly()`. Respond with `504` (in fit mode: with the best result so far if any compile succeeded).
7. Limits: source ≤ `MAX_SOURCE_BYTES` (1 MB), PDF ≤ `MAX_PDF_BYTES` (10 MB; larger → `422`), log in the response — the last 4000 characters.
8. No Docker fallback inside the worker: `typst` must be in the image.

#### Fit mode (`"fit": true`)

The template reads `sys.inputs.scale` (default `1.0`) and scales font size, leading and spacing. The worker searches the scale; bounds `MIN=0.88`, `MAX=1.12`, target fill `0.93`, at most 7 compiles, bracket precision `0.005`:

1. Compile at `1.0`. One page with fill >= 0.93: done, `X-Page-Scale: 1.000`.
2. Otherwise compile the far bound: `MIN` if the first compile had more than one page, else `MAX`. Two pages at `MIN`, or one page at `MAX` (fill may be below 0.93): return that compile as is.
3. Otherwise bisect between the known one-page scale and the known failing scale for the largest scale that gives exactly one page. A one-page result beats any overflow; among one-page results the largest scale wins.
4. The PDF, page metrics and `X-Page-Scale` of the winner are returned. A compile error at any scale is `422`; running out of the shared deadline returns the best so far, or `504` if nothing compiled.

A compiled document cannot report its own scale, hence the header. Without `fit` there is a single compile, no scale input and no `X-Page-Scale`.

### 3.4 Configuration (env)

| Variable | Default | Purpose |
|---|---|---|
| `WORKER_PORT` | `8090` | HTTP port |
| `WORKER_TOKEN` | — (required) | shared secret with `core-api`; empty → the worker does not start |
| `WORKER_CONCURRENCY` | number of cores | concurrent compilations |
| `COMPILE_TIMEOUT_SECONDS` | `60` | limit for one compilation |
| `MAX_SOURCE_BYTES` | `1048576` | input limit |
| `MAX_PDF_BYTES` | `10485760` | output limit |
| `TYPST_BIN` | `typst` | path to the binary |

### 3.5 Docker image and sandbox

Image:

- Multi-stage build: stage 1 builds the jar, stage 2 is a minimal JRE plus the pinned static `typst` binary (v0.15.1, musl, downloaded per architecture from the GitHub release).
- No cache, no warm-up at build time: Typst is a single static binary with embedded fonts. The worker compiles `warmup.typ` at start for `/readyz`.
- The process runs as an unprivileged user (`USER 10001`).

The runtime (docker-compose / k8s) MUST set:

- `read_only: true` for the root filesystem, with writes only to a `tmpfs` for `/tmp`;
- `cpus` and `mem_limit` (guideline: 1 CPU and 1 GB per compile slot);
- `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]`;
- **no internet egress**: a separate network with `internal: true`, shared only with `core-api`;
- the worker port MUST NOT be published to the outside.

---

## 4. Changes in core-api

### 4.1 Database schema

A new Flyway migration with the next free version number. MUST NOT edit existing migrations.

```sql
CREATE TABLE compile_job (
    id            UUID PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    source_hash   VARCHAR(64)  NOT NULL,                 -- sha256 hex of the Typst source
    source        TEXT,                                  -- cleared after completion
    status        VARCHAR(16)  NOT NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    error_code    VARCHAR(32),
    error_message TEXT,
    lease_until   TIMESTAMPTZ,                           -- until when a RUNNING job is "held"
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    CONSTRAINT ck_compile_job_status CHECK (status IN ('QUEUED','RUNNING','DONE','FAILED'))
);

CREATE INDEX idx_compile_job_queued ON compile_job (created_at) WHERE status = 'QUEUED';
CREATE INDEX idx_compile_job_lease  ON compile_job (lease_until) WHERE status = 'RUNNING';
-- at most one active job per user and source (dedupes double clicks)
CREATE UNIQUE INDEX uq_compile_job_active
    ON compile_job (user_id, source_hash) WHERE status IN ('QUEUED','RUNNING');

CREATE TABLE resume_pdf_cache (
    source_hash   VARCHAR(64) PRIMARY KEY,
    pdf           BYTEA       NOT NULL,
    size_bytes    INT         NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

Rules:

- The JPA status is `@Enumerated(EnumType.STRING)` + `VARCHAR` + `CHECK`, as with `ApplicationStatus`. Do not use a Postgres `ENUM`.
- Hashes are `VARCHAR(64)`, not `CHAR(64)`: Hibernate's `ddl-auto: validate` rejects a `bpchar` column for a `String` field.
- The cache key is a hash of the **source**, not `resumeVersion`: a template or renderer change must invalidate the cache automatically.
- The cache is shared across users: the same source yields the same PDF. PDF access goes only through the owner's `compile_job` (see 4.3); a PDF is not served by hash alone.

### 4.2 Package and classes

New package `com.jobsearch.core_api.compile`:

| Class | Responsibility |
|---|---|
| `CompileJob`, `CompileJobStatus` | JPA entity and enum |
| `CompileJobRepository` | queries, including claim (native SQL) |
| `ResumePdfCache`, `ResumePdfCacheRepository` | PDF cache |
| `CompileDtos` | `CompileJobResponse` and similar — records |
| `CompileJobService` | `enqueue()`, `get()`, `pdf()` — what the controller calls |
| `CompileDispatcher` | background loop: claim → call the worker → write the result |
| `CompileJanitor` | return expired leases and delete old rows |
| `PdfWorkerClient`, `PdfWorkerConfig` | `RestClient` to the worker, modeled on the project's other `RestClient` wrappers |
| `CompileController` | endpoints from 4.3 |

The old synchronous `ResumeController.compile()` is removed after the cutover (see section 8).

### 4.3 HTTP API for the frontend

| Method | Path | Response |
|---|---|---|
| POST | `/api/resume/compile` | `202` + `CompileJobResponse`. A cache hit is also `202`, but already `status: DONE`. If an active job with the same hash already exists, that job is returned (a new one is not created). |
| GET | `/api/resume/compile/{jobId}` | `200` + `CompileJobResponse`; someone else's job or a missing job → `404` |
| GET | `/api/resume/compile/{jobId}/pdf` | `200 application/pdf` (`Content-Disposition: inline; filename="resume.pdf"`); job is not `DONE` → `409`; someone else's job → `404` |

```json
// CompileJobResponse
{ "id": "uuid", "status": "QUEUED|RUNNING|DONE|FAILED",
  "error": null | { "code": "compile_error", "message": "..." },
  "createdAt": "...", "finishedAt": null | "..." }
```

Rules:

- Every request MUST be filtered by `currentUserService.requireUserId()`, as in `VacancyService` and `JobApplicationService`. Another user's `jobId` → `404`, not `403` (do not reveal that it exists).
- `POST /api/resume/compile` is already in `ExpensiveOpsRateLimitFilter.LIMITED_EXACT_PATHS` — keep it there. Polling GET endpoints are NOT covered by that limit (the filter only looks at POST).
- For `FAILED` with `compile_error`, `message` includes the tail of the compiler log: the user needs it to see which resume field breaks the document. For other errors, a generic message with no infrastructure details.

### 4.4 enqueue — algorithm

1. Load the resume (`ResumeService.get()`), render Typst (`ResumeTypstRenderer`), compute `sha256` (hex).
2. A short transaction:
   - a row exists in `resume_pdf_cache` → update `last_used_at`, create a `compile_job` already in `DONE` (`source = NULL`), return;
   - otherwise insert a `compile_job` (`QUEUED`, `source`). A violation of `uq_compile_job_active` (a parallel request won the race) → return the existing active job with a follow-up SELECT. `enqueue` is therefore not `@Transactional`: the violation would abort a surrounding transaction and the SELECT could not run.
3. No HTTP calls inside this method.

### 4.5 CompileDispatcher — algorithm

Runs in every `core-api` instance; several instances safely share the queue thanks to `SKIP LOCKED`.

```sql
-- claim: atomically take the oldest job from the queue
UPDATE compile_job
SET status = 'RUNNING', attempts = attempts + 1,
    started_at = NOW(), lease_until = NOW() + make_interval(secs => :leaseSeconds)
WHERE id = (
    SELECT id FROM compile_job
    WHERE status = 'QUEUED'
    ORDER BY created_at
    FOR UPDATE SKIP LOCKED
    LIMIT 1
)
RETURNING *;
```

Rules:

1. Poll loop: every 500 ms, while local slots are free (`Semaphore(dispatch-concurrency)`), run claim. Each claimed job is handled on a virtual thread.
2. **A transaction wraps only the claim and the result write.** The HTTP call to the worker is strictly outside a transaction (`TransactionTemplate`, as in `AtsMatchService`), so a pool connection is not held.
3. Handling the worker response:

   | Worker response | Action |
   |---|---|
   | `200` | in one transaction: `INSERT ... ON CONFLICT (source_hash) DO UPDATE SET last_used_at = NOW()` into the cache; job → `DONE`, `source = NULL`, `finished_at` |
   | `422`, `413`, `400` | → `FAILED` (permanent), store `error_code`/`error_message` |
   | `503 busy` | → back to `QUEUED`, **decrement `attempts`** (busyness does not count as an attempt); the dispatcher pauses for `Retry-After` |
   | `504`, `500`, network error, client timeout | `attempts < max-attempts` → back to `QUEUED`; otherwise `FAILED` with code `worker_unavailable` |

4. Before writing the result, check that the job is still `RUNNING` and belongs to this attempt (`WHERE id = ? AND status = 'RUNNING' AND attempts = ?`). If the row was not updated, the lease was already taken back — discard the result.
5. `leaseSeconds` = the worker HTTP client timeout + 30 s of slack.
6. Graceful shutdown (`@PreDestroy`): Spring stops the scheduler, so no new claims; the dispatcher waits up to 30 s for in-flight jobs. `CompileJanitor` picks up anything unfinished via the expired lease.

### 4.6 CompileJanitor

Runs every 30 s (`@Scheduled`; `@EnableScheduling` lives in `SchedulingConfig`, which tests switch off with `app.scheduling-enabled=false` to drive the dispatcher and janitor by hand). Every operation is idempotent; running concurrently on several instances is safe.

- `RUNNING` with `lease_until < NOW()` → `QUEUED` if `attempts < max-attempts`, otherwise `FAILED` (`worker_unavailable`).
- `QUEUED` older than `queue-max-age` (default 5 min) → `FAILED` (`queue_timeout`): the user is no longer waiting.
- Delete `compile_job` rows older than 7 days and `resume_pdf_cache` rows with `last_used_at` older than 30 days (batches of 500 rows).

### 4.7 Configuration (`AppProperties.PdfWorker`, prefix `app.pdf-worker`)

| Property | Env in `application.yml` | Default |
|---|---|---|
| `base-url` | `PDF_WORKER_BASE_URL` | `http://127.0.0.1:8090` |
| `token` | `PDF_WORKER_TOKEN` | — (empty → `core-api` does not start, same as `APP_ADMIN_EMAIL`) |
| `read-timeout-seconds` | `PDF_WORKER_READ_TIMEOUT_SECONDS` | `75` (longer than the worker compile timeout) |
| `connect-timeout-seconds` | — | `3` |
| `dispatch-concurrency` | — | `4` |
| `max-attempts` | — | `3` |
| `queue-max-age-seconds` | — | `300` |

Only the first three are wired to env vars and listed in `.env.example`; the rest are plain defaults in
`AppProperties.PdfWorker` that can still be overridden as `app.pdf-worker.*`. The poll interval (500 ms) is a
constant in `CompileDispatcher`.

---

## 5. Shared code rules

Apply to both services. They match the style already used in `core-api`.

1. **Constructor injection**, `private final` fields. No `@Autowired` on fields.
2. **DTOs are `record`s** nested in `*Dtos` classes. Timestamps in responses are ISO-8601 strings (`Instant.toString()`), as in the other DTOs.
3. **Errors:** `NotFoundException` → 404, `ConflictException` → 409, `BadRequestException` → 400 (its message is written for the user), `ServiceUnavailableException` → 503 (via the existing `ApiExceptionHandler`). Any other exception, including `IllegalStateException` and `IllegalArgumentException`, is a 500 whose message is only logged. Do not introduce new exceptions unless necessary.
4. **No network calls inside transactions.** New code that does HTTP inside `@Transactional` is rejected in review.
5. **Logs are `key=value`** (`log.info("Compile done jobId={} millis={} bytes={}", ...)`). MUST NOT log the Typst source, the PDF, tokens, or resume contents.
6. **Timeouts on every external call**: connect and read are set explicitly; there are no unbounded waits.
7. **Configuration only through `AppProperties` / env**. No hardcoded URLs, tokens, or limits in code, other than defaults.
8. Comments in English, and only where the "why" is not obvious (as in the existing code).
9. Virtual threads are for blocking waits (HTTP to the worker), not for CPU work.

---

## 6. Observability

Logs (minimum):

- core-api: `enqueued jobId userId cacheHit`, `claimed jobId attempt waitMillis`, `finished jobId status workerMillis totalMillis`, `requeued jobId reason`.
- worker: `compile ok millis sourceBytes pdfBytes`, `compile failed code millis`, `busy`.

Metrics (once actuator/Micrometer is wired in):

- `compile.queue.depth` — number of `QUEUED`;
- `compile.queue.wait` — from `created_at` to `started_at`;
- `compile.duration` — time on the worker;
- `compile.cache.hit` / `compile.cache.miss`;
- `compile.result{status}`.

The signal that more workers are needed: `compile.queue.wait` and the rate of `503 busy` grow while `compile.duration` stays stable.

---

## 7. Tests (minimum for acceptance)

**pdf-worker**

- Successful compilation of `warmup.typ` → `200`, body starts with `%PDF`.
- A syntax error → `422` with a log.
- A document with a very long loop → `504`; the `typst` process is gone after the response.
- Fit mode: short text scales up (> 1.0) and fills the page, long text scales down and still gives one page, hopeless text returns two pages at the minimum scale, text that already fits returns `1.000` after one compile; `X-Page-Scale` is present only in fit mode.
- All slots busy → `503` with `Retry-After`.
- A wrong or missing token → `401`.
- After every request the temp directory is deleted.

**core-api**

- enqueue: a cache hit → `DONE` with no call to the worker; a double POST → one active job.
- Two parallel dispatchers do not claim the same job (integration test on Postgres, Testcontainers).
- The worker-response branches from the table in 4.5 (worker is `MockRestServiceServer` or WireMock).
- The janitor returns a job with an expired lease and fails a job after `max-attempts`.
- Another user's `jobId` → `404` on both GETs.

---

## 8. Rollout plan

1. Worker: code, Dockerfile, tests. A `pdf-worker` service in `docker-compose.yml` (internal network, port not published outside; for local development a separate `ports` binding only on `127.0.0.1`). No separate `dev:pdf` script: `npm run dev` already runs `infra:up` (`docker compose up`), which starts the worker. After changing worker code, rebuild it with `docker compose build pdf-worker`.
2. core-api: migration, `compile` package, new endpoints. The old synchronous `POST /api/resume/compile` keeps working at this step — the new POST temporarily lives at `/api/resume/compile-jobs`.
3. Frontend: `api/client.ts` — `startCompile()`, `getCompileJob()`, PDF download; poll with backoff 500 ms → 1 s → 2 s, overall limit 2 min; show `compile_error` to the user.
4. Cutover: point the frontend at the new endpoints; rename `/api/resume/compile-jobs` to `/api/resume/compile` (update `ExpensiveOpsRateLimitFilter`); delete the old synchronous `ResumeController.compile()`.
5. Update `core-api/README.md` and the root `README.md` (new service, variables, ports).

---

## 9. Open questions

- Do we need a synchronous "fast path" (wait up to ~3 s and return the PDF immediately if it finished)? That simplifies the frontend but complicates the API. Default: no.
- Where to host workers in production and how to autoscale them (by CPU or by `compile.queue.depth`).
- Whether to keep PDFs in Postgres as volume grows, or bring back object storage. At ~50–150 KB per PDF and cleanup after 30 days, Postgres is enough.

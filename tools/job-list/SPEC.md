# job-list — job collection and ranking service — specification

This document describes **what** we are building and **the rules** for writing the code of `tools/job-list`: a server that collects vacancies from several job boards, asks an LLM which of them fit a person's profile and expectations, and serves the ranked result to its consumers (`core-api` of job_search and a Telegram bot).

How binding the rules are: **MUST / MUST NOT** — do not violate; **SHOULD** — depart only with an explanation in the PR.

---

## 1. Goals and boundaries

**Goals**

1. Given a *search profile* (regions, roles, free-text expectations), the service regularly collects fresh vacancies from four sources, removes duplicates and returns the best matches, each with a score and a short reason.
2. The ranking behaviour is controlled by a prompt the owner writes (a file in the repository). Changing the prompt re-ranks jobs; nothing else needs a code change.
3. What to search is a **filter module**: regions and roles now, more dimensions later (remote policy, salary, seniority, language, company size…) by adding one class each, without schema migrations and without touching the pipeline.
4. Two kinds of consumers use the same API: `core-api` (several users, each with their own profiles) and a Telegram bot (one or a few chats). Neither needs to know anything about Apify or the LLM.
5. Cost is bounded and visible. The service starts on Apify's **free plan** (a small monthly credit), so it enforces a **monthly budget** itself: every run has a hard cap per source and a cap on LLM work, its real cost is stored, and a source is skipped when the month's budget would be exceeded. Moving to a paid plan later changes one setting.
6. One source breaking (an actor changes its output, an outage) never breaks the others.

**Out of scope for v1**

- Applying to jobs, CV tailoring (that is `core-api`).
- Our own scrapers. Sources are Apify actors, called through the Apify API.
- Push delivery (webhooks). Consumers poll; the Telegram bot polls too.
- Message brokers, Redis, Elasticsearch, vector search. Postgres only.
- User accounts and a UI. Clients are other services, authenticated by a token.
- Multi-instance operation. v1 is one replica; the design must not make scaling out hard (section 12), but does not implement it.
- Storing the same job under several sources (cross-source duplicates are dropped, see 5.3).

---

## 2. Architecture

```
 core-api / telegram bot                    job-list (1 replica)                        outside
 ───────────────────────                    ────────────────────                        ───────
 POST /v1/profiles          ───────────────▶ SearchProfile (roles, regions)
 POST /v1/profiles/{id}/runs ──────────────▶ Run (QUEUED)
                                                      │
                                              RunPipeline ◀─ RunWorker (claims QUEUED runs)
                                               1. roles, regions → SourceQuery
                                               2. JobSource × 4  ──────────────────▶ Apify actors (HTTP, async + poll)
                                               3. normalize + post-filter + dedupe
                                               4. JobRanker  ──────────────────────▶ OpenRouter (LLM)
                                               5. store matches
 GET /v1/profiles/{id}/matches ◀───────────── Match (score, reason)
 POST /v1/matches/{id}/ack
```

**Decisions**

- **A pipeline, not a framework.** `RunPipeline` is one class with five short steps called in order. No Spring Batch, no Quartz, no event bus.
- **The queue is a Postgres table** (`run`), claimed with `FOR UPDATE SKIP LOCKED`, exactly like the job engine already used in `core-api`. This keeps a later second replica possible.
- **Sources hide behind one interface** (`JobSource`, four implementations). The ranker is a concrete class: there is only one, so no interface (no speculative abstraction).
- **Own database** (`job_list`) with its own user. Nothing is shared with `core-api`'s tables; the services only talk over HTTP.
- **No hard filter on fetched jobs.** The roles and regions only tell the actors what to fetch; every new job is ranked by the model. A title or place filter written by hand drops good jobs (synonyms, other languages, "Paris 8e"). A cheap title triage by the model is the planned way to cut cost; it is not built yet.
- **Budget guard before spending money on Apify:** before each source is fetched, `spent this month + that source's cost cap` must fit in `APIFY_MONTHLY_BUDGET_USD`; otherwise the source is skipped (section 7, 12).

---

## 3. Stack and layout

- Java 25, Spring Boot 4.x (same as `core-api`), `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `spring-boot-starter-actuator`, `spring-boot-starter-validation`, PostgreSQL 16, Jackson 3 (`tools.jackson`), `RestClient`, virtual threads. Tests: JUnit 5, Mockito, Testcontainers (`testcontainers-postgresql`), `MockRestServiceServer` for HTTP clients.
- Maven wrapper in the module, like the other modules. Package `com.jobsearch.joblist`, **package by feature**:

```
tools/job-list/
├─ pom.xml  mvnw  .mvn/  Dockerfile  SPEC.md  README.md
└─ src/main/java/com/jobsearch/joblist/
   ├─ JobListApplication.java
   ├─ api/        ProfileController, MatchController, FilterController, RunController,
   │              ApiError, ApiExceptionHandler, ClientAuthFilter
   ├─ mcp/        McpController, McpTools, McpTool   ← MCP over the same services as api/
   ├─ profile/    SearchProfile, SearchProfileRepository, SearchProfileService, ProfileDtos
   ├─ source/     JobSource, ApifyClient, SourceResult,
   │              (shared: ApifyClient, ApifyFetcher, HtmlText, JsonFields)
   │              linkedin/  LinkedInSource  LinkedInMapper
   │              hellowork/ HelloWorkSource HelloWorkMapper
   │              greenhouse/GreenhouseSource GreenhouseMapper
   │              wttj/      WttjSource      WttjMapper
   ├─ job/        Job, Remote, ContractType, JobRepository, JobStore, JobFingerprint
   ├─ rank/       JobRanker, RankPrompt, LlmClient, Match, MatchService, MatchDtos, RankAnswerParser, Fence
   ├─ run/        Run, RunSource, RunPipeline, RunWorker, RunService, UsageService, Janitor
   └─ config/     JobListProperties, HttpClientsConfig
src/main/resources/
   ├─ application.yml
   ├─ prompts/rank.md            ← the prompt the owner writes
   └─ db/migration/V1__baseline.sql
src/test/resources/fixtures/     one real captured actor response per source
```

Start with `README.md` of the module covering how to run it locally and the env table; this spec stays the source of the rules.

---

## 4. Data model (Flyway `V1__baseline.sql`)

All timestamps are `timestamptz`. Ids are `bigint` identity, except where noted.

```
search_profile
  id, client            text   -- which client owns it (from the token), tenant boundary
  owner_ref             text   -- opaque id the client uses (a core-api user id, a Telegram chat id)
  name                  text
  expectations          text   -- free text for the LLM: what the person wants and does not want
  roles                 text[] -- job titles to search for, at least one
  regions               text   -- JSON list of {country, city?}; country is an ISO 3166-1 alpha-2 code
  sources               text[] -- which of LINKEDIN, HELLOWORK, GREENHOUSE, WTTJ to use
  min_score             int    -- default 70; what "a match" means for GET .../matches
  created_at, updated_at
  UNIQUE (client, owner_ref, name)

job                                       -- normalised vacancy, shared by all profiles
  id, source, external_id
  fingerprint           text   -- sha256(company|title|country|city), see 5.3
  url, title, company, description_text
  countries, cities     text[] -- every country and city the posting names
  remote                text   -- ON_SITE | HYBRID | REMOTE | UNKNOWN
  contract_type         text   -- FULL_TIME | PART_TIME | CONTRACT | INTERNSHIP | TEMPORARY | OTHER | UNKNOWN
  salary_min, salary_max numeric, salary_currency text, salary_period text  -- HOUR|DAY|MONTH|YEAR
  skills                text[]
  posted_at, fetched_at
  UNIQUE (source, external_id)
  UNIQUE (fingerprint)

match
  id, profile_id → search_profile ON DELETE CASCADE, job_id → job ON DELETE CASCADE
  score                 int    -- 0..100
  reason                text
  prompt_hash           text   -- sha256 of prompt + expectations at ranking time
  ranked_at, delivered_at  timestamptz null
  UNIQUE (profile_id, job_id)

run
  id, profile_id → search_profile ON DELETE CASCADE
  status                text   -- QUEUED | RUNNING | SUCCEEDED | PARTIAL | FAILED
  started_at, finished_at, lease_until
  fetched, new_jobs, ranked int
  cost_usd              numeric  -- scraping + estimated LLM
  error                 text

spend_ledger                              -- what was spent (and reserved) on Apify; no foreign keys, see section 12
  kind                  text   -- APIFY | LLM
  source, query_key     text   -- the source and the key of the paid call (see QueryKeys); null for LLM
  profile_id, run_id    bigint -- informational, not foreign keys: deleting a profile or pruning runs keeps the money
  apify_run_id          text
  status                text   -- RESERVED | SUCCEEDED | FAILED | LOST
  cost_usd              numeric  -- RESERVED and LOST rows count at the per-source cap
  created_at, settled_at timestamptz

rank_failure                              -- jobs the model could not rank for a profile and prompt; two failures = not offered again
  profile_id → search_profile, job_id → job (both ON DELETE CASCADE), prompt_hash, failures
  PRIMARY KEY (profile_id, job_id, prompt_hash)

client_run_start                          -- one row per queued run, kept when the profile is deleted (no foreign keys)
  client, owner_ref text, created_at

run_source                                -- one row per source per run (what happened; the money is in spend_ledger)
  run_id → run ON DELETE CASCADE, source text
  status                text   -- SUCCEEDED | FAILED | SKIPPED
  apify_run_id          text
  fetched, new_jobs, dropped int   -- dropped = items the mapper could not use
  cost_usd              numeric
  error                 text
  UNIQUE (run_id, source)   -- plus a surrogate id
```

Rules:

- **Only fields that the ranker, the filters or a consumer use are stored** (no language, no model name). No raw actor payload, no recruiter / hiring-manager names, emails or photos (data minimisation; those fields exist in some actors' output and MUST be dropped by the mappers).
- Indexes: `job(posted_at)`, `match(profile_id, score DESC, id DESC) WHERE delivered_at IS NULL`, `match(job_id)`, `run(status, lease_until)`.
- **Retention:** a janitor deletes jobs with `fetched_at` older than `job-list.retention-days` (default 60); matches follow by cascade; runs older than 90 days are deleted.

---

## 5. Sources

### 5.1 The `JobSource` interface

```java
interface JobSource {
    SourceName name();
    SourceResult fetch(SourceQuery query);   // blocking; returns normalised jobs + cost + counters
}
```

- A source that takes a single query (WTTJ, LinkedIn, HelloWork's location) uses the **first** role and the first region of the query. Each implementation has two parts, both small: the **Source** (builds the actor input from `SourceQuery`, calls `ApifyClient`) and the **Mapper** (one actor item → `Job`, or "not usable"). The mapper is a pure function and is tested against a real captured fixture.
- `ApifyClient` is the only class that knows the Apify HTTP API: start a run (`POST /v2/acts/{actor}/runs`, with a hard cost cap `maxTotalChargeUsd` and `memory` where the actor needs it), poll `GET /v2/actor-runs/{id}` until it finishes or the timeout passes, then read the dataset (`GET /v2/datasets/{id}/items`, paginated). Calls are blocking on virtual threads; every call has connect and read timeouts. A run still going after `job-list.apify.run-timeout` is aborted (`POST …/abort`), and its source is recorded as FAILED.
- **Sources can be switched off in configuration** (`job-list.sources.<name>.enabled`). A disabled source cannot be chosen in a profile (400) and is never called. `LINKEDIN` is **disabled by default** and is the last source to be built (section 16).
- Each source has a `min-interval-hours` (default 24; Greenhouse 72, because its 200-job minimum makes every run cost the same): a source is not called again for the **same paid call** within it. The call is identified by the source and a **query key** each source builds from what it really sends to its actor (`JobSource.queryKey`): WTTJ and LinkedIn send only the first role and first region, so that pair is the key; HelloWork sends every role and one French place; Greenhouse every role and place. Case and spaces are ignored, and so are the window and the item limit. The wait is therefore per search, not per profile: a profile deleted and saved again, or a second profile with the same search, does not pay again (its jobs are in the shared pool anyway).
- **The ledger decides whether a paid call may start** (`SpendLedger.reserveApify`). It reserves the worst case (the per-source cap) *before* Apify is called and settles with the real cost after. The checks (the run is still RUNNING, the same call was not made within the interval, spent this month + cap fits the budget) and the insert happen in one short transaction under `pg_advisory_xact_lock`, so concurrent runs or replicas cannot both pass; the call to Apify is made after the transaction. A call counts against the interval while reserved, lost, successful, or when it cost money; one Apify refused to start (cost 0) does not. A run that dies leaves its reservation: the cost stays counted at the cap, which is also the ceiling the actor was started with (`maxTotalChargeUsd`), and the actor is started with `timeout` = the run timeout, so an orphaned Apify run ends by itself. A failed start is counted by what Apify said: a 4xx means nothing ran (cost 0, the search can be retried at once); a 5xx, a timeout or a lost answer may have created a run, so it is counted at the cap and the search waits out the interval.
- A source failing (HTTP error, actor failed, output unreadable) is recorded in `run_source` and the run continues; the run ends **PARTIAL** if at least one source worked and at least one failed, **FAILED** if none worked.
- **Schema drift guard:** the mapper drops an item that lacks the fields a job needs (`external_id`, `url`, `title`, `company`, a description). If more than `job-list.mapper-drop-threshold` (default 30 %) of a source's items were dropped, the source is recorded as FAILED with the message "output format changed?" — the sign that an actor was updated.

### 5.2 The four sources

Actor ids, input fields and prices are taken from the actors' public pages when this spec was written. **The exact input and output field names MUST be confirmed on each actor's Input/API tab and by one real run before the mapper is written; the captured response becomes the test fixture.** Prices change; the cost cap protects the budget, not this table.

| Source | Actor | Strengths | Notes for the adapter |
|---|---|---|---|
| `LINKEDIN` | `curious_coder/linkedin-jobs-scraper` | widest coverage, full description | from ≈ $1 / 1000 results. Input is a LinkedIn search URL or structured filters (keywords, location, date posted, …). Since 2026 LinkedIn supports only date posted, company, easy apply and "under 10 applicants" as real filters; other filters become natural language in the keywords (the actor's "auto convert" option, on by default). A search returns at most 1000 jobs (the actor can split by location). Output has recruiter name, title, photo and profile URL — **not stored**. |
| `HELLOWORK` | `dltik/hellowork-scraper` | French market | $1 / 1000 + $0.004 per job with `enrichDetails`. Input: `keywords[]`, `location`, `contractTypes[]`, `minSalary`, `remote`, `maxResults`, `skipReposts`. Use `enrichDetails=true` (skills, structured salary, full description) and **`analyzeJobs=false`, `enrichCompany=false`**: ranking is ours, and each paid option multiplies the cost. Output is snake_case (`company`, `city`, `contract_type`, `salary_min`, `published_at`, `url`, …). |
| `GREENHOUSE` | `fantastic-jobs/greenhouse-jobs-api` | jobs on companies' own career sites, rich AI fields | from $1.20 / 1000. A **database** of scraped jobs, refreshed twice an hour, not a live scrape. Run size is 200–5000 jobs (a minimum cost per run). Search: `titleSearch`, `locationSearch` (**English, exact "City, State/Region, Country"**, no abbreviations), `descriptionSearch` (slow, avoid), `aiWorkArrangementFilter`, `aiExperienceLevelFilter`, `hasSalary`, `aiLanguageFilter`, `removeAgency`, `includeCompanyDetails`. Choose the plain-text description. No date filter: rely on our dedupe. |
| `WTTJ` | `clearpath/welcome-to-the-jungle-jobs-api` | Europe, tech, salary and benefits | from $2.99 / 1000. Input: `query`, `websiteCountry`, `location`, `locationRadius`, `countryCode`, `sortBy`, `datePosted` (1/3/7/14/30 days), contract type, `remote[]`, `experienceLevel`, `salaryMin`, `language`, `includeDetails`, `maxItems`. Descriptions are HTML → convert to text in the mapper. |

### 5.3 Normalisation and de-duplication

- The mapper produces a `Job` with the common enums of section 4. Unknown values map to `UNKNOWN`; the mapper never throws on a missing optional field.
- **Description text:** HTML is converted to plain text (one shared helper), collapsed whitespace, capped at `job-list.max-description-chars` (default 6000) — the cap saves LLM tokens and storage.
- **Fingerprint** = `sha256(lowercase(company) | lowercase(title) | country | lowercase(city))` after stripping punctuation and a trailing "(m/f/d)", "H/F" and similar gender markers. It catches the same vacancy on two boards and reposts. `UNIQUE(fingerprint)` makes the first source win; an insert conflict is counted as a duplicate, not an error.
- A job already stored (same `source+external_id`) is skipped. Rows are immutable after insert; v1 does not track edits to a posting.

---

## 6. Search input

A profile holds `roles` (job titles, at least one) and `regions` (`{country, city?}`, optional). They are not filters: they are the words and the place that each source passes to its actor (`SourceQuery`). Sources that take a single query use the first role and the first region. Nothing is dropped after the fetch, the model sees every new job.

Mapping region → source format lives in each source's code (LinkedIn location string, HelloWork city, Greenhouse English city or country name, WTTJ `countryCode`/`location`).

---

## 7. Run pipeline

`RunPipeline.execute(run)` calls these steps in order; each step is a short method of its own (no deep nesting; early returns).

1. **Build the query:** `SearchProfileService.query(profile)` turns the profile's roles and regions into a `SourceQuery`. `lookbackDays` is computed **per source** from the last successful ledger row of the same paid call (any profile; days since it, rounded up to what the sources support: 1, 3, 7, 14, 30), or `job-list.first-run-lookback-days` (default 3) the first time; so a failed source does not lose its window. `maxItems` = `job-list.max-items-per-source` (default 50).
2. **Fetch** each enabled source in the profile (`JobSource.fetch`), in the profile's source order, one after another in v1 (simple, and the cost cap is per source). Before each one: skip it (`SKIPPED`; the reason is stored: source disabled, query not supported, fetched less than N hours ago, monthly budget) when the ledger refuses the reservation (section 12). A failure is recorded and the loop goes on.
3. **Store:** insert the fetched jobs (`ON CONFLICT DO NOTHING` on both unique keys); count `new_jobs`.
4. **Choose candidates and rank:** the candidates are the stored jobs with `fetched_at` within `job-list.rank-window-days` (default 7) and have **no `match` row for this profile with the current `prompt_hash`**, newest first, at most `job-list.max-ranked-per-run` (default 40: a profile saved now is ranked against the shared pool, which holds jobs fetched for other searches, so this is the biggest lever on what a new profile costs). This one rule covers jobs another profile's run stored, jobs left over by the cap (they stay candidates until the window passes), and re-ranking after a prompt or expectations change. A `match` row is stored for every ranked job, including scores below `min_score` (so it is not ranked again); re-ranking updates `score` and `reason` and leaves `delivered_at` as it is.
5. **Finish:** update the run row (counts, cost, status).

Rules:

- **No HTTP call inside a database transaction.** A transaction wraps only the writes of a single step.
- A run row is created `QUEUED` by the API or MCP (a search runs **only on request**, there is no schedule) and executed by `RunWorker`: it claims with `FOR UPDATE SKIP LOCKED` and sets `lease_until` for one step (the slowest of an Apify run and a model call, plus 5 minutes); the pipeline **renews the lease before every source and every ranking batch**, and an update that touches no row means the run was released, so the worker stops (`RunLostException`). The result is written with `UPDATE … WHERE status = 'RUNNING'`, so a run the janitor released stays released. `Janitor` marks runs with an expired lease `FAILED` ("lost", guarded by `lease_until < now`), and at startup every run still `RUNNING` (there is one instance and the Deployment is `Recreate`, so it was cut off by the restart; with several replicas this startup release must go). The reservation of the source in progress stays in the ledger as `LOST`, counted at the cap.
- **Cost accounting:** a run that Apify reports as costing nothing, or one whose result could not be read after it started, is counted at the per-source cap (pay-per-event charges may not show in `usageTotalUsd`; verify against a real run). A source switched off in configuration after a profile was saved is skipped (`source disabled`). The posting date is checked against the retention period when jobs are stored.
- The month of the budget is the UTC calendar month.
- One run per profile at a time: a second request while one is `QUEUED`/`RUNNING` returns the existing run (409-free idempotency).
- The run's `cost_usd` is the sum of the Apify run costs (read from the finished Apify run) plus the LLM estimate from the token counts and the configured per-token prices.

---

## 8. Ranking (LLM)

- **Client:** OpenRouter, same API shape as in `core-api`. The model is configurable (`job-list.llm.model`); the default is **`deepseek/deepseek-chat`** (cheap, and already in the model list of `core-api`). Use a **separate OpenRouter key** with its own credit limit, so ranking cannot drain the key `core-api` uses. Explicit timeouts; ranking is sequential on the one worker, so there is no limit on parallel calls to manage. Ask for JSON output with `response_format` when the model supports it; the validation below is the guarantee either way.
- **Language:** the prompt, the profile's `expectations` and the `reason` are in **English**. Postings can be in other languages (French, German…): the prompt tells the model to read them as they are and answer in English.
- **Prompt:** `src/main/resources/prompts/rank.md` — written by the owner — is the system prompt. `JobRanker` adds, per call: the profile's `expectations` and a batch of jobs. The file is read once at startup; `prompt_hash = sha256(prompt + expectations)`. Editing the prompt or the expectations changes the hash, so existing matches are ranked again on the next run (within the cap above).
- **Input to the model:** a batch of `job-list.llm.batch-size` jobs (default 10), each as `id`, `title`, `company`, `location`, `remote`, `contract_type`, `salary`, `skills`, `description_text` (already capped). No other personal or internal data.
- **Output contract (added by the code after the owner's prompt, so editing the prompt cannot break it):** one JSON object `{"results": [{"id": <job id>, "score": <integer 0..100>, "reason": "<one or two sentences>"}]}`.
- **Validation, entry by entry:** every balanced `{…}` in the answer is a candidate (braces inside strings do not count) and the first one with a `"results"` key is the answer, so fences, a sentence before or after it, even a stray brace in that prose, do not matter. Each entry with a whole-number `id` of a job we sent (the first one for that id; `85`, `85.0` and `"85"` all count as whole numbers), a whole-number `score` in range and a non-blank `reason` (trimmed to 500 chars) is **used**; every other entry is ignored and its job counts as missing, so one bad entry never throws away the good ones. No default score is ever invented.
- **When an answer is incomplete** (`JobRanker`):
  - *Some entries usable:* the jobs it left out are asked once more, alone (a second call). The ones still missing failed on their own and are recorded in `rank_failure` (per profile, job and prompt hash). **After two failures a job is not offered again for that prompt** (a changed prompt or expectations give it a fresh chance), so one unrankable posting is not paid for on every run and cannot hold a place in the per-run cap for ever.
  - *No entry usable* (not JSON, cut off by `max_tokens`, every entry bad): nothing says which job is at fault, so **nobody is blamed**. The batch is split once and each half is asked (with the two steps above, but no further split); a half that is fine is ranked. When both halves rank nothing the model is not giving usable answers now: the pass stops ("The model keeps giving answers that cannot be used"), the run ends PARTIAL, and the jobs stay candidates for the next run (an outage of the model never hides jobs).
  - *A job that fails alone* is blamed only as a half whose other half was ranked (the model was answering). A lone job that is a whole batch is not blamed, since nothing shows that the model works: it is asked again on every run, one call. The split is one level deep, so a half that returns garbage is not narrowed further (healthy jobs next to a poison pair that spoils the whole answer stay unranked, three calls per run, nobody blamed).
  - A batch costs one or two calls, at most five (the batch, then two halves of two calls each), and valid entries are always kept. No default score is ever invented.
- **Cost bounds on the model:** every call carries `max_tokens` (200 per job + 100: an entry with the longest reason the parser accepts), so one call cannot run on; every call is **reserved in the ledger** (`kind LLM`) at `job-list.llm.max-cost-per-call-usd` (default 0.01) before it is made and settled with OpenRouter's reported cost after it. **When the cost is not known** (no `usage.cost` in the answer, or a timeout or 5xx that may have been billed) the reservation stays at its worst case (the row becomes `LOST`), so the cap cannot silently stop working; only a refusal before generation (4xx) is booked as zero. A cost above the reservation is logged with the setting to raise. A call that would take the month's model spending over `job-list.llm.monthly-budget-usd` (default 2) is not made (the run ends PARTIAL, "The monthly model budget is used up"), and **a source is not fetched at all** when the model budget has no room for one more call (reason `model budget`), since the jobs could not be ranked. The lease is renewed and the run checked before every call.
- **Limits per client:** a client may start at most `job-list.max-runs-per-owner-per-day` runs per owner (default 3) and `job-list.max-runs-per-client-per-day` runs in all (default 200) in 24 hours (429; a run that is already waiting is returned, not counted again), and hold at most `job-list.max-profiles-per-client` profiles (default 100). The count of started runs is in `client_run_start`, which has no foreign keys, so deleting a profile does not reset it. Owner names are chosen by the client, so only the per-client numbers are real ceilings; `core-api`, one client for all its users, gets its per-user share from the per-owner limit. Together with the Apify rules (section 12) the money a client can make this service spend is bounded whatever it does with profiles and runs; the monthly budgets are shared by all clients.
- **Prompt injection:** a posting is untrusted text. Each job is wrapped in fenced blocks (`<job id="…">…</job>`, with the closing tag neutralised if it appears inside the text) and the system prompt states that fenced text is data, not instructions — the same rule `core-api` uses.
- **Logging:** counts, ids, model, tokens, milliseconds. MUST NOT log job descriptions, the profile's expectations, or the bodies of failed HTTP calls (only the status or the exception class).
- **Cost control levers, in order of use:** the description cap, the batch cap per run, the cheaper model. A two-stage ranking (cheap triage, then a stronger model on the top) is the next step if the volume grows; it is **not** built now.

---

## 9. HTTP API

JSON, `/v1`, bearer-token authentication. Timestamps are ISO-8601 strings.

| Method and path | Purpose |
|---|---|
| `POST /v1/profiles` | create a profile `{ownerRef, name, expectations, roles, regions?, sources, minScore?}` → 201; `regions` are checked against the ISO country codes |
| `GET /v1/profiles?ownerRef=` | the client's profiles, optionally one owner's |
| `GET /v1/profiles/{id}` · `PUT` · `DELETE` | read, replace, delete (matches and runs go with it) |
| `POST /v1/profiles/{id}/runs` | start a run now → 202 with the run (or the active one) |
| `GET /v1/profiles/{id}/runs` | recent runs with per-source results and cost |
| `GET /v1/profiles/{id}/matches?minScore=&delivered=&since=&limit=&cursor=` | ranked matches, best first (`delivered=true` returns only delivered ones, otherwise only those not yet delivered); `minScore` defaults to the profile's; each item has the job (title, company, location, remote, salary, url, postedAt, skills, snippet), `score`, `reason` |
| `POST /v1/matches/{id}/ack` | the consumer marks a match delivered (`delivered_at`), so the bot does not send it twice |
| `GET /v1/usage` | this month's Apify spend: `{month, spentUsd, budgetUsd, bySource}`, so the owner sees the free credit being used |
| `GET /actuator/health/liveness`, `/actuator/health/readiness` | probes, no token (as in `core-api`); readiness = database reachable, no external dependency |

### 9.1 MCP interface (for the Telegram bot's agent)

The same service also speaks **MCP** (Model Context Protocol), so an agent can use it as a tool server without custom glue code. `core-api` uses the REST API above; the bot's agent uses MCP. Both go through the same services (`SearchProfileService`, the match queries, `RunService`), so there is one implementation of every behaviour.

- **Transport:** Streamable HTTP at `/mcp`, same bearer-token authentication and the same client isolation as `/v1`. If the bot's runtime can only start local (stdio) MCP servers, it runs a generic stdio-to-HTTP bridge pointed at this URL; nothing changes in `job-list`.
- **Tools** (names are verbs; arguments and results are the same records as the REST DTOs):

  | Tool | Does |
  |---|---|
  | `list_profiles` | the caller's search profiles |
  | `save_profile` | create or update a profile (`name`, `expectations`, `roles`, `regions`, `sources`, `minScore`) |
  | `run_search` | start a run for a profile now |
  | `get_matches` | best undelivered matches for a profile (limit ≤ 20) |
  | `mark_delivered` | `ack` a match once it was sent to the user |
  | `get_usage` | this month's Apify spend and remaining budget |

- **Deliberately not exposed:** deleting a profile, changing tokens or budgets. An agent can be talked into things; destructive actions stay in the REST API. Cost-raising actions that remain (`run_search`) are bounded by the budget guard, the source intervals and the per-owner profile limit.
- `save_profile` creates or updates the profile with the given `name` for the given `ownerRef` (both arguments of the tool; the agent passes its chat id as `ownerRef`).
- **Untrusted text:** a posting that comes back through `get_matches` is read by the bot's LLM. The tool returns only the job's facts, the `score`, the `reason` and a short snippet (≤ 300 chars) with the URL, and labels the snippet as third-party text, never the full description. This limits what a hostile posting can say to the agent.
- **Implementation:** decided by a spike at the start of phase 6 (the official MCP Java SDK, which brings its own Jackson 2, or a minimal hand-written Streamable HTTP endpoint if the SDK does not fit Boot 4.1.1 and Jackson 3). Whatever is chosen, the client identity travels in a request attribute (not a thread-local), because SDK handlers may run on other threads; the tools are annotated methods in `McpTools` with no logic of their own beyond mapping arguments. Adding or removing a tool never touches the pipeline.

Rules:

- **Clients:** `job-list.clients` is a list of `{name, token}` from configuration. The token identifies the client; a client sees only the profiles whose `client` equals its name (a request for another client's id is a 404). Tokens are compared in constant time.
- Errors use the same exception→status mapping as `core-api` (`BadRequestException` 400 with a user-readable message, `NotFoundException` 404, the framework's client errors keep their 4xx, anything else 500 with the detail only in the log). The body is always `{status, error, message}`.
- `GET /v1/usage` is global (there is one Apify account). `since` filters on `ranked_at`; the `matches` cursor is `score:id` of the last item returned. `PUT` naming a disabled source is a 400.
- **Per-user profiles:** `core-api` creates profiles with `ownerRef` = its user id. A client MUST NOT exceed `job-list.max-profiles-per-owner` (default 2): every profile is a recurring cost. `core-api` is trusted to map its own users; `job-list` does not know them.
- Request sizes are bounded (`expectations` ≤ 5000 chars, ≤ 20 regions, ≤ 30 roles).
- Pagination is by cursor (`match.id` or `score,id`), `limit` ≤ 100.

---

## 10. Configuration (env → `JobListProperties`, prefix `job-list`)

| Env | Default | Meaning |
|---|---|---|
| `JOB_LIST_DB_URL`, `_USER`, `_PASSWORD` | — | own database (strong password check like `core-api`'s strict mode) |
| `JOB_LIST_CLIENT_0_NAME`, `_0_TOKEN`, `JOB_LIST_CLIENT_1_NAME`, `_1_TOKEN` | — | allowed clients (`core-api`, `telegram-bot`); tokens at least 16 characters |
| `APIFY_TOKEN` | — | Apify API token |
| `APIFY_RUN_TIMEOUT_SECONDS` | 600 | wait for one actor run |
| `APIFY_MAX_COST_PER_SOURCE_USD` | 0.30 | `maxTotalChargeUsd` per actor run |
| `JOB_LIST_MAX_ITEMS_PER_SOURCE` | 50 | items requested per source per run (Greenhouse cannot go below its own 200 minimum) |
| `APIFY_MONTHLY_BUDGET_USD` | 5 | spend ceiling per calendar month; the free credit amount must be checked on the Apify account |
| `JOB_LIST_SOURCE_<NAME>_ENABLED` | `LINKEDIN` false, others true | switch a source on or off |
| `JOB_LIST_SOURCE_<NAME>_MIN_INTERVAL_HOURS` | 24 (`GREENHOUSE` 72) | do not fetch the same source for the same profile more often |
| `JOB_LIST_MAX_PROFILES_PER_OWNER` | 2 | profiles per `ownerRef` |
| `JOB_LIST_FIRST_RUN_LOOKBACK_DAYS` | 3 | window of the first run |
| `JOB_LIST_MAX_RANKED_PER_RUN` | 40 | LLM work cap per run |
| `JOB_LIST_LLM_MAX_COST_PER_CALL_USD`, `JOB_LIST_LLM_MONTHLY_BUDGET_USD` | 0.01, 2 | what a model call is reserved at in the ledger, and the monthly model budget |
| `JOB_LIST_MAX_RUNS_PER_OWNER_PER_DAY`, `JOB_LIST_MAX_RUNS_PER_CLIENT_PER_DAY`, `JOB_LIST_MAX_PROFILES_PER_CLIENT` | 3, 200, 100 | runs per owner and per client in 24 hours (429 beyond them), profiles per client |
| `JOB_LIST_MAPPER_DROP_THRESHOLD` | 0.3 | share of unusable items that fails a source |
| `JOB_LIST_RETENTION_DAYS` | 60 | job retention |
| `OPENROUTER_API_KEY`, `OPENROUTER_BASE_URL` | — | LLM access (a key of its own) |
| `JOB_LIST_LLM_MODEL` | `deepseek/deepseek-chat` | ranking model |
| `JOB_LIST_LLM_BATCH_SIZE`, `JOB_LIST_LLM_TIMEOUT_SECONDS` | 10, 120 | LLM call shape (ranking is sequential on the one worker) |
| `APIFY_BASE_URL`, `APIFY_POLL_INTERVAL_SECONDS` | Apify's API, 5 | Apify access |
| `JOB_LIST_WORKER_POLL_SECONDS`, `JOB_LIST_RANK_WINDOW_DAYS`, `JOB_LIST_RUN_RETENTION_DAYS`, `JOB_LIST_MAX_DESCRIPTION_CHARS` | 10, 7, 90, 6000 | worker, ranking window, retention, description cap |
| `JOB_LIST_SCHEDULING_ENABLED` | true | switches the background loops off (tests) |

Secrets are environment only. A missing `APIFY_TOKEN` or `OPENROUTER_API_KEY` makes the service refuse to start.

---

## 11. Code rules

Apply to the whole module; they match the style already used in `core-api` and `latex-worker`.

1. **Readability first.** A method does one thing, fits on a screen (≈ 30 lines), has at most two levels of nesting, and uses early returns. Prefer a named private method to a comment.
2. **No dead or speculative code.** No unused fields, parameters, columns, config keys, endpoints, or "for later" hooks. An interface exists only when it has two or more implementations (`JobSource`); everything else is a concrete class.
3. **Constructor injection**, `private final` fields, no `@Autowired` on fields.
4. **DTOs are `record`s** in `*Dtos` classes; domain value objects (`Job`, `SourceQuery`, `RankedJob`) are records too where JPA does not need a class.
5. **Errors:** reuse the `core-api` set of exceptions (`BadRequestException`, `NotFoundException`, `ServiceUnavailableException`); do not add new ones without a reason. Source and LLM failures are values (`SourceResult`, `RankReport`), not exceptions that escape the pipeline.
6. **No network calls inside transactions.**
7. **Timeouts on every external call** (Apify, OpenRouter); no unbounded waits or retries (one retry for an invalid LLM answer; none for Apify — the next requested run is the retry).
8. **Logs are `key=value`** (`log.info("Source done runId={} source={} fetched={} new={} costUsd={}", …)`). MUST NOT log tokens, descriptions, expectations or Apify payloads. MDC carries `runId`.
9. **Configuration only through `JobListProperties`.** No hard-coded URLs, tokens, limits.
10. **Comments in English, only for the "why".**
11. **Optimisation seams, not optimisations:** the code is arranged so that these can be added without a rewrite — a second replica (claim by `SKIP LOCKED` already), parallel sources (the loop in step 2 of the pipeline), a second LLM stage (inside `JobRanker`), a new source (one package), a title triage before the ranking (a step between 3 and 4). None of them is implemented before it is needed.

---

## 12. Scaling and operations

- **v1:** one replica, sources fetched sequentially, small volumes (section 13).
- **Spending the free credit well** (in the order the pipeline applies them):
  1. narrow the search: specific regions and roles are the biggest saving;
  2. `lookbackDays` from the last success, so a repeated run asks only for new postings;
  3. `max-items-per-source` low (default 50) and a hard `maxTotalChargeUsd` per actor run;
  4. `min-interval-hours` per source (Greenhouse every 3 days, its minimum run costs the same either way);
  5. the monthly budget guard skips a source rather than overspending; `GET /v1/usage` shows what is left;
  6. dedupe against the database before the LLM sees anything, and rank only jobs not yet ranked for the current prompt;
  7. decide per source, from the captured fixtures, which mode returns a usable description at the lowest price (for HelloWork, `enrichDetails`; for WTTJ, `includeDetails`) and use that mode only.
- **After testing**, when the volume or the number of users grows: raise the budget or move Apify to a paid plan (settings only); run sources in parallel (virtual threads, one `fetch` each); share one fetch between profiles whose `SourceQuery` is identical within the run window (not in v1); add the two-stage ranking (cheap triage, then a stronger model on the top) inside `JobRanker`; run a second replica (the claim is already `SKIP LOCKED` with a lease).
- **Observability in v1:** the logs, the `run` / `run_source` tables (counts, cost, errors per source), `GET …/runs` and `GET /v1/usage`. Prometheus metrics are a later addition (the project does not run Prometheus yet).

---

## 13. Cost picture (orientation only)

Using the prices in 5.2 for one run with 50 items per source: LinkedIn ≈ $0.05, HelloWork ≈ $0.25 with enrichment, Greenhouse ≈ $0.24 (its 200-job minimum), WTTJ ≈ $0.15 — about **$0.7 for all four**. Run daily by hand that would be ≈ $20 a month, which is **more than a free credit of about $5**; with the defaults (Greenhouse every 3 days, `LINKEDIN` off, one profile) it is roughly $0.5 a day, ≈ $15 a month, and the budget guard stops the spending at `APIFY_MONTHLY_BUDGET_USD`. So during testing run on demand or every few days, with one or two sources, and watch `GET /v1/usage`. The LLM with `deepseek/deepseek-chat` costs cents per day at this volume (≈ 100 ranked jobs × ~1.5 k tokens).

---

## 14. Security and privacy

- Token authentication on every `/v1` endpoint; clients are isolated from each other.
- **The Telegram bot runs outside the cluster** (a separate Hermes agent process), so `job-list` is also served over HTTPS at its own hostname (for example `jobs.cvhelper.dev`, an A record to the same server, a certificate from the existing cert-manager issuer, a Traefik rate-limit middleware). **Only `/mcp` is routed** there: the REST API, which can delete profiles, is reachable by `core-api` inside the cluster only. The bot's token is the only thing protecting it: it is long and random, kept in the bot's configuration, and can be rotated by changing one environment variable. `core-api` uses the internal service name and never goes through the public route.
- The service sends job text and the person's `expectations` to the LLM provider and search terms to Apify. This MUST be stated in the privacy notes of the consuming apps.
- Job postings contain personal data of recruiters. The mappers drop it (section 4); only job and company information is stored.
- Third-party actors are community-maintained and scrape sites whose terms may forbid it (LinkedIn in particular). That is a business risk accepted by the owner; the source list is isolated so a source can be disabled in one place (the profile's `sources` and one bean).
- Job text is untrusted input to the LLM (section 8). Nothing from a job is ever executed or fetched by the server.
- The container runs as a non-root user, read-only root file system, `/tmp` as `emptyDir`, no service-account token — the same hardening as the other services in `deploy/k8s`.

---

## 15. Tests (minimum for acceptance)

1. **Mappers** (one test class per source): the captured real response in `src/test/resources/fixtures/` maps to the expected `Job`s; an item missing a required field is dropped; recruiter personal data is not present in the result.
2. **Profiles:** roles and regions are required or checked (an unknown country code, no roles).
3. **Fingerprint:** the same vacancy with different casing, punctuation or "(m/f/d)" gives the same fingerprint; different cities give different ones.
4. **Pipeline** (Testcontainers Postgres, fake `JobSource` and fake LLM): new jobs are stored once; a second run adds nothing and ranks nothing; changing the prompt hash re-ranks; one failing source gives `PARTIAL` and keeps the other sources' jobs; all failing gives `FAILED`; the rank cap leaves the rest for the next run.
5. **Ranker:** the valid entries of a partly bad answer are stored and only the missing jobs are asked again (at most two calls per batch); fenced or wrapped JSON is read; every call carries `max_tokens` and is reserved and settled in the ledger; the monthly model budget stops the pass; the injection wrapper neutralises a closing tag inside a description. **LlmClient** (mock HTTP): the request, fenced/wrapped JSON, a cut-off answer, refusals explained by cause (401, 402, 404, 429), an unreachable provider. **Run limit:** the daily limit per client.
6. **ApifyClient** (mock HTTP server): start, poll, read paginated dataset, timeout aborts the run, cost cap parameter is sent.
7. **Budget and switches:** a source is skipped (`SKIPPED`, reason `monthly budget`) when `spent this month + cost cap` exceeds the monthly budget, and (reason `fetched less than N hours ago`) when the same paid call was made within `min-interval-hours`; both survive deleting the profile; parallel reservations at the budget edge admit exactly as many as fit; a lost run's reservation stays counted; a disabled source is never called and a profile naming it gets 400; `GET /v1/usage` adds up `spend_ledger.cost_usd` for the month; the profile limit per owner is enforced.
8. **MCP:** the tools list matches 9.1; a tool called with another client's profile id fails like a 404 on the REST API; `get_matches` never returns a full description; stdio bridging needs no server change (checked by a plain HTTP client test against `/mcp`).
9. **API:** authentication (no token, wrong token, another client's profile → 404), validation limits, `matches` ordering and `minScore`, `ack` moves a match from the default list to `delivered=true`.
10. **Janitor:** an expired lease is marked `FAILED` and a renewed one is left alone; a worker that wakes up after its release writes nothing; old jobs, runs and ledger rows are deleted; cascades remove matches.

---

## 16. Rollout plan

1. **Skeleton:** module, `pom.xml`, Flyway baseline, auth filter, `/healthz`, profiles CRUD (`roles`, `regions`).
2. **First source end to end:** `ApifyClient` + `HelloWorkSource` (cheapest to verify), with the monthly budget guard and `GET /v1/usage` from the start with a captured fixture, `RunPipeline` without ranking, fake LLM. Run it by hand against Apify with a small `maxItems`.
3. **Ranking:** `JobRanker`, the first version of `prompts/rank.md` (owner), `match` endpoints and `ack`.
4. **WTTJ and Greenhouse**, each as its own package with fixture and mapper test. **`LINKEDIN` last**, disabled by default, switched on in configuration only when wanted.
5. **Janitor:** lease recovery, retention.
6. **Deploy:** Dockerfile, `deploy/k8s/job-list.yaml`, secrets in `make-secrets.sh` (`APIFY_TOKEN`, a separate `OPENROUTER_API_KEY`, one token per client), a network policy (ingress from `core-api` and from Traefik, egress to Apify and OpenRouter), CI image job, and the public route for the bot (section 14).
7. **Consumers:** the bot (a Hermes agent process, section 14) connects over MCP (9.1) and uses `get_matches` / `mark_delivered`; `core-api` gets a thin client (one profile per user, `ownerRef` = user id) and a page later.

---

## 17. Decisions taken and what is still open

**Decided**

1. The Telegram bot is a separate process (Hermes agent) outside the cluster: it calls the public HTTPS route with its own client token (section 14).
2. One search profile per `core-api` user (`ownerRef` = user id), at most two each (section 9).
3. The prompt, the expectations and the `reason` are in English.
4. Apify free plan first; monthly budget guard, small defaults, per-source intervals (section 12). Moving to a subscription is a configuration change.
5. Ranking model: `deepseek/deepseek-chat` through OpenRouter, with its own key.
6. Server capacity is not a constraint for now; revisit after the first deployment.
7. `LINKEDIN` is built last and is switchable in configuration, off by default.

**Still open (answer during steps 1–3)**

1. **Does the Hermes agent connect to remote MCP servers over HTTP with a bearer header, or only to local stdio servers?** This decides whether the bot talks to `/mcp` directly or through a small bridge (9.1); either way `job-list` itself does not change.
2. **The free credit of the Apify account:** the real monthly amount, to set `APIFY_MONTHLY_BUDGET_USD`.
3. **Apify actor input and output field names** of the four actors, by one real run each (section 5.2).
4. **First version of `prompts/rank.md`** — written by the owner; test it on about 50 real jobs and compare `deepseek/deepseek-chat` with a stronger model once.

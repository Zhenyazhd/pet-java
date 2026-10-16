# job-list — development plan

Implements `SPEC.md`. Rules from the spec apply (readable, no dead code, interfaces only with 2+ implementations). Nothing is committed by the agent; the owner commits.

## Phases

| # | Phase | Contents | Done when |
|---|---|---|---|
| 0 | Skeleton | `pom.xml` (Boot 4.1.1, Java 25, same deps as core-api minus security), `mvnw`, `application.yml`, `JobListProperties`, Flyway `V1__baseline.sql`, entities + repositories, `ClientAuthFilter`, `ApiExceptionHandler` + exceptions, the actuator health probes | module builds, context starts on Testcontainers |
| 1 | Profiles | `SourceQuery`, `Region`, `SearchProfileService`, `ProfileController`, per-owner limit. **No filter module: nothing is dropped before the ranking** | profile API tests pass |
| 2 | Pipeline core | `ApifyClient`, `ApifyFetcher` (run, poll, dataset, mapping loop, drop threshold — shared by all sources), `JobSource`/`SourceResult`, `HtmlText`, `HelloWorkSource` + mapper + fixture, `JobFingerprint`, `RunPipeline`, **`RunWorker` (claim `SKIP LOCKED`, lease, executes QUEUED runs)**, `RunController`, budget guard, `GET /v1/usage` | pipeline test with fake source passes; a manual run executes end to end |
| 3 | Ranking | `RankPrompt`, `JobRanker`, OpenRouter client, `MatchController` (`matches`, `ack`), `prompts/rank.md` placeholder | ranker + match tests pass |
| 4 | More sources | WTTJ, Greenhouse (subagent, in parallel with phase 3); LinkedIn last, disabled by default | one mapper test each |
| 5 | Janitor | `Janitor` (expired leases, restart release), retention. **No schedule: a search runs only on request** | janitor tests pass |
| 6 | MCP | **spike first** (does the official MCP Java SDK resolve with Boot 4.1.1 and Jackson 3? if not, a minimal hand-written Streamable HTTP endpoint: `initialize`, `tools/list`, `tools/call`), then the tools over the same services | MCP test passes with the bearer filter |
| 7 | Deploy | Dockerfile, `deploy/k8s/job-list.yaml`, kustomization entry, network policy, public ingress (`jobs.cvhelper.dev`) + Traefik rate limit, one-off creation of the `job_list` database and role in the existing Postgres, CI image job, `make-secrets.sh`, README | image builds, manifests render |

## Order and who does what

1. Plan check: `advisor` subagent (1).
2. Phases 0–2: main thread (shared foundation; interfaces must exist before others build on them).
3. Phase 3 in the main thread; phase 4 (WTTJ, Greenhouse) by one `general-purpose` subagent started when these exist and are frozen: `JobSource`, `SourceQuery`, `SourceResult`, `ApifyFetcher`, `HtmlText`, the `Job` entity with its enums, `SourceName`, the `sources` settings and yml entries, and the HelloWork source as the example. The subagent touches only `source/wttj/**`, `source/greenhouse/**` and their tests/fixtures; anything shared comes back to the main thread (2).
4. Review of phases 0–5 by `backend-reviewer` (3); fixes in the main thread.
5. Phases 6–7, then the final `backend-reviewer` pass over the whole module (4).

At most four subagents in total.

## Known limits

- Actor input/output field names come from the actors' public pages. No Apify token is available here, so mapper fixtures are **documentation samples** (marked as such in the fixture file). This is a deliberate deviation from SPEC 5.2 ("capture first"); each fixture MUST be replaced by a real capture after one small run per actor, and the mapper test re-run.
- No real LLM call is made in tests (fake client).

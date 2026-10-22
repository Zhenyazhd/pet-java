# job-list

Collects vacancies from four job boards (through Apify actors), asks an LLM which of them fit a person's profile and expectations, and serves the ranked result over a REST API and over MCP. Used by `core-api` (one profile per user) and by a Telegram bot.

`SPEC.md` describes what it does and the rules for the code; `PLAN.md` the order it was built in.

## Run it locally

Needs Java 25 and a PostgreSQL database named `job_list`.

```bash
export JOB_LIST_DB_PASSWORD=...            # the database role's password
export JOB_LIST_CLIENT_0_TOKEN=$(openssl rand -hex 24)   # core-api
export JOB_LIST_CLIENT_1_TOKEN=$(openssl rand -hex 24)   # telegram-bot
export APIFY_TOKEN=...                      # https://console.apify.com/settings/integrations
export OPENROUTER_API_KEY=...               # a key of its own, with a credit limit
./mvnw spring-boot:run
```

Tests need Docker (Testcontainers): `./mvnw test`.

## Try it

```bash
TOKEN=$JOB_LIST_CLIENT_0_TOKEN
curl -s -X POST localhost:8100/v1/profiles -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{
  "ownerRef": "me", "name": "Backend in France",
  "expectations": "Python or Java backend, hybrid or remote, France, senior level.",
  "roles": ["python developer", "backend engineer"],
  "regions": [{"country": "FR", "city": "Paris"}]
}'

curl -s -X POST localhost:8100/v1/profiles/1/runs -H "Authorization: Bearer $TOKEN"     # start now
curl -s localhost:8100/v1/profiles/1/runs -H "Authorization: Bearer $TOKEN"              # progress and cost
curl -s localhost:8100/v1/profiles/1/matches -H "Authorization: Bearer $TOKEN"           # best first
curl -s localhost:8100/v1/usage -H "Authorization: Bearer $TOKEN"                        # month's Apify spend
```

## Roles and regions: the order matters

WTTJ and LinkedIn take a single query, so they search only the **first** role and the first region of a profile. HelloWork searches all roles but only the first French city; Greenhouse takes all of them. Put the most important role and region first. Searching every pair would cost one actor run each, which the free Apify credit does not cover.

## What it can spend, and what stops it

Everything that costs money goes through one ledger (`spend_ledger`), and a call is only made after its worst case was reserved there.

| Limit | Default | Setting |
|---|---|---|
| Apify per month | $5 | `APIFY_MONTHLY_BUDGET_USD` |
| one Apify run (also its time limit is the run timeout) | $0.30 | `APIFY_MAX_COST_PER_SOURCE_USD`, `APIFY_RUN_TIMEOUT_SECONDS` |
| the same search on the same source | once in 24 h (Greenhouse 72 h), whatever the profile | `JOB_LIST_SOURCE_<NAME>_MIN_INTERVAL_HOURS` |
| the model per month | $2 | `JOB_LIST_LLM_MONTHLY_BUDGET_USD` |
| what one model call is reserved at (real cost is booked after) | $0.01 | `JOB_LIST_LLM_MAX_COST_PER_CALL_USD` |
| jobs ranked per run | 40 | `JOB_LIST_MAX_RANKED_PER_RUN` |
| runs per owner / per client in 24 h (then `429`) | 3 / 200 | `JOB_LIST_MAX_RUNS_PER_OWNER_PER_DAY`, `JOB_LIST_MAX_RUNS_PER_CLIENT_PER_DAY` |
| profiles per owner / per client | 2 / 100 | `JOB_LIST_MAX_PROFILES_PER_OWNER`, `JOB_LIST_MAX_PROFILES_PER_CLIENT` |

`GET /v1/usage` (MCP `get_usage`) shows what was spent this month, what is reserved for calls in flight and what is counted at the worst case for calls whose end was lost. Budgets are shared by all clients. Details and the reasons: `SPEC.md` sections 11 and 12.

## Where to change things

| To change | Edit |
|---|---|
| how jobs are ranked | `src/main/resources/prompts/rank.md` (the output format is fixed in `RankPrompt`) |
| what a profile searches for | `ProfileDtos` and `SearchProfileService.query`, then the `*Source.java` that should use it |
| a source's actor input or output fields | `source/<name>/*Source.java`, `*Mapper.java` |
| budgets, limits, switches | `application.yml` / the `JOB_LIST_*` and `APIFY_*` environment variables |
| the MCP tools | `mcp/McpTools.java` |

The mapper fixtures in `src/test/resources/fixtures` are documentation samples, not real captures: after one small real run per actor, replace each and re-run the mapper tests (see the README in that folder).

## Deploying

`deploy/README.md`, section "job-list".

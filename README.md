# Job Search

Personal job-search workspace: structured resume editing with AI, vacancy targeting, CV file versions, and application tracking.

## Repository layout

| Path | Role |
|------|------|
| [`core-api/`](core-api/) | Spring Boot HTTP API (Postgres, S3, OpenRouter, LaTeX → PDF) |
| [`frontend/`](frontend/) | React + Vite UI |
| [`tools/ats-screener/`](tools/ats-screener/) | ATS Screener (git submodule) — local scanner on `:5174` |
| [`docker-compose.yml`](docker-compose.yml) | Local Postgres + S3Mock |
| [`.env.example`](.env.example) | Environment template |

## Documentation

- **Backend:** [core-api/README.md](core-api/README.md) — stack, config, schema, full HTTP API
- **Frontend:** TBD (separate commits)

## Quick start (one command)

Prerequisites: Docker, Java 25, Node **≥ 22.13** (for ATS; frontend works on 18+), `pnpm` 10+, optional `tectonic`.

```bash
cp .env.example .env
# set OPENROUTER_API_KEY in .env
# ATS keys: cp tools/ats-screener/.env.example tools/ats-screener/.env
#   then add GEMINI_API_KEY / GROQ_API_KEY

git submodule update --init --recursive
npm run setup          # root + frontend + ats deps
npm run dev            # postgres/s3 + api + web + ats
```

| Service | URL |
|---------|-----|
| API | http://localhost:8080 |
| App UI | http://localhost:5173 |
| ATS Screener | http://127.0.0.1:5174/scanner |
| Postgres | localhost:5432 |
| S3Mock | localhost:9090 |

Useful variants:

```bash
npm run dev:core       # infra + api + frontend (no ATS)
npm run infra:down     # stop Postgres / S3
```

Ctrl+C stops API, web, and ATS; containers keep running until `infra:down`.

### Manual (separate terminals)

```bash
docker compose up -d
cd core-api && ./mvnw spring-boot:run
cd frontend && npm run dev
cd tools/ats-screener && pnpm exec vite dev --host 127.0.0.1 --port 5174
```

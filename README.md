# Job Search

Personal job-search workspace: structured resume editing with AI, vacancy targeting, CV file versions, and application tracking.

## Repository layout

| Path | Role |
|------|------|
| [`core-api/`](core-api/) | Spring Boot HTTP API (Postgres, S3, OpenRouter, LaTeX → PDF) |
| [`frontend/`](frontend/) | React + Vite UI |
| [`docker-compose.yml`](docker-compose.yml) | Local Postgres + S3Mock |
| [`.env.example`](.env.example) | Environment template |

## Documentation

- **Backend:** [core-api/README.md](core-api/README.md) — stack, config, schema, full HTTP API
- **Frontend:** TBD (separate commits)

## Quick start (infra)

```bash
cp .env.example .env
docker compose up -d
```

Then start the API and UI (see backend README; frontend docs coming later):

```bash
cd core-api && ./mvnw spring-boot:run
# in another terminal
cd frontend && npm install && npm run dev
```

- API: `http://localhost:8080`
- UI: `http://localhost:5173`

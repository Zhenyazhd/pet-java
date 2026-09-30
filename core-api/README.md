# core-api

HTTP API for the Job Search platform: vacancies and applications, CV files in S3, structured resume (JSON → LaTeX → PDF), user profile with career path, and AI section edits via OpenRouter.

Listens on `http://localhost:8080` by default. The Vite frontend calls `/api/**` from origins listed in `app.cors.allowed-origins` (default `http://localhost:5173`).

---

## Stack

| Component | Technology |
|-----------|------------|
| Runtime | Java 25 |
| Framework | Spring Boot **4.1.1** (Web MVC, Validation, Data JPA) |
| Database | PostgreSQL 16 + **Flyway** (`ddl-auto: validate`) |
| Object storage | AWS SDK v2 S3 → local **adobe/s3mock**, later R2/AWS |
| PDF | **tectonic** (native on `PATH`, otherwise Docker `dxjoke/tectonic-docker`) |
| AI | [OpenRouter](https://openrouter.ai) Chat Completions (`response_format: json_object`) |
| JSON | Jackson 3 (`tools.jackson`) |

---

## Running locally

### Dependencies

1. PostgreSQL and S3Mock:

```bash
# from repo root
cp .env.example .env   # adjust passwords/ports if needed
docker compose up -d
```

2. Native **tectonic** (recommended for fast PDF builds):

```bash
brew install tectonic
```

Without it, the API falls back to Docker (slower, especially on Apple Silicon).

3. OpenRouter API key in `.env` / environment: `OPENROUTER_API_KEY=...`

### Start the app

```bash
cd core-api
./mvnw spring-boot:run
```

Environment variables come from the shell / IDE / root `.env` (if exported). Main settings live in `src/main/resources/application.yml` and the root `.env.example`.

### Build

```bash
./mvnw -q compile -DskipTests
./mvnw test
```

---

## Configuration (`app.*`)

| Property / env | Description | Default |
|----------------|-------------|---------|
| `app.admin-email` / `APP_ADMIN_EMAIL` | Bootstrap ADMIN created/promoted on startup (**required** in `prod`; no code default) | empty |
| `app.bootstrap-password` / `APP_BOOTSTRAP_PASSWORD` | Password set on bootstrap admin if hash missing | empty |
| `app.cors.allowed-origins` / `APP_CORS_ALLOWED_ORIGINS` | Comma-separated SPA origins (credentials) | `http://localhost:5173` |
| `app.auth-rate-limit.login-per-minute` / `APP_AUTH_LOGIN_PER_MINUTE` | Max login attempts per IP per minute (`0` = off) | `10` |
| `app.auth-rate-limit.register-per-minute` / `APP_AUTH_REGISTER_PER_MINUTE` | Max register attempts per IP per minute (`0` = off) | `5` |
| `app.auth-rate-limit.trust-forwarded-headers` / `APP_AUTH_TRUST_FORWARDED_HEADERS` | Honor `X-Forwarded-For` / `X-Real-IP` only from trusted proxies | `false` |
| `app.auth-rate-limit.trusted-proxies` / `APP_AUTH_TRUSTED_PROXIES` | Comma-separated peer IPs allowed to set forwarded client IP | `127.0.0.1,::1` |
| `app.expensive-ops-rate-limit.per-user-per-minute` / `APP_EXPENSIVE_OPS_PER_USER_PER_MINUTE` | Max AI/ATS/import calls per user per minute (`0` = off) | `10` |
| `app.s3.*` / `S3_*` | Endpoint, region, keys, bucket, path-style | localhost:9090, bucket `job-search-cvs` |
| `app.open-router.api-key` / `OPENROUTER_API_KEY` | OpenRouter API key | empty |
| `app.open-router.model` / `OPENROUTER_MODEL` | Chat model | `openai/gpt-4o-mini` |
| `app.open-router.base-url` | API base URL | `https://openrouter.ai/api/v1` |
| `app.ats-screener.base-url` / `ATS_SCREENER_BASE_URL` | Local ATS Screener (`tools/ats-screener`) | `http://127.0.0.1:5174` |
| `app.ats-screener.timeout-seconds` | HTTP read timeout for ATS analyze | `180` |
| `POSTGRES_*` | JDBC to Postgres | see `application.yml` |

Multipart: max file **10MB**, request **12MB**.

---

## Package layout

```
com.jobsearch.core_api
├── ai/              OpenRouter client + AI suggest for resume sections
├── ats/             Proxy to local ATS Screener (vacancy match scores)
├── common/          NotFoundException, ConflictException, ApiExceptionHandler
├── config/          AppProperties, CorsConfig, S3Config
├── cv/              CV file versions + sendings linked to vacancies
├── jobapplication/  Application status (1:1 with vacancy)
├── latex/           Compile arbitrary LaTeX → PDF
├── profile/         app_user: name, email, career_path
├── resume/          Structured resume JSON, LaTeX render, plain-text, compile
├── storage/         ObjectStorageService (S3)
└── vacancy/         Vacancies and requirements
```

Typical Spring layers: **Controller → Service → Repository / external clients**. DTOs are Java `record`s in `*Dtos` classes.

---

## Data model (Flyway)

Migrations: `src/main/resources/db/migration/`.

### V1 — vacancies and applications

- **`vacancy`** — url (unique), title, company, description, match_percent (0–100)
- **`vacancy_requirement`** — name + required, unique (vacancy_id, name)
- **`job_application`** — exactly one application per vacancy; status ∈  
  `NOT_APPLIED | APPLIED | INTERVIEW | OFFER | REJECTED | WITHDRAWN`

### V2 — user and CV files

- **`app_user`** — seed: id=`1`, `me@local`
- **`cv_version`** — file metadata + S3 `storage_key`
- **`application_cv`** — “this CV version was sent to this vacancy” (sending history)

### V3 — career path

- `app_user.career_path` TEXT — biography / facts for AI

### V4 — structured resume

- `app_user.resume_json` TEXT — resume document JSON  
  If empty, the API returns the template from `classpath:default-resume.json`.

---

## Authentication (temporary)

No auth. Current user = `app.current-user-id` (usually `1`).  
Profile, resume, and CV versions are scoped to that id. Replace later with a real session/JWT.

---

## HTTP API

Base prefix: `/api`. Errors use a uniform JSON body (see below).

### Profile — `/api/profile`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/profile` | `displayName`, `email`, `careerPath` |
| PUT | `/api/profile` | save all fields |
| GET | `/api/profile/career-path` | career path only |
| PUT | `/api/profile/career-path` | career path only |

Career path is the AI’s **primary factual source** (employers, dates, skills) — the model must not invent a biography.

### Resume — `/api/resume`

Structured document (not raw `.tex`):

```json
{
  "name": "...",
  "headline": "...",
  "phone": "...",
  "email": "...",
  "linkedinUrl": "...",
  "linkedinLabel": "...",
  "profile": "...",
  "experience": [{ "title", "subtitle", "dates", "bullets": [] }],
  "education": [{ "title", "subtitle", "location", "details" }],
  "achievements": [{ "title", "text" }],
  "skills": [{ "category", "items" }]
}
```

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/resume` | load JSON (or default) |
| PUT | `/api/resume` | persist to `app_user.resume_json` |
| GET | `/api/resume/latex` | `{ "source": "<tex>" }` via `ResumeLatexRenderer` |
| POST | `/api/resume/compile` | current JSON → LaTeX → PDF (`application/pdf`) |

### AI — `/api/ai`

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/ai/resume/suggest` | ongoing chat / edit — full resume (`section: "all"`) or one focused block |
| POST | `/api/ai/resume/match` | score saved resume vs vacancy via ATS Screener (`tools/ats-screener`) |

**Request (`SuggestRequest`):**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `section` | string | yes | `all` (full resume) or `header` \| `profile` \| `experience` \| `education` \| `achievements` \| `skills` |
| `instruction` | string | yes | user message |
| `itemIndex` | int | no | list index (for experience / education / achievements / skills) |
| `history` | `{role, content}[]` | no | recent chat turns |
| `vacancyContext` | string | no | vacancy text from the UI (Add context) — wording targeting |
| `model` | string | no | OpenRouter model id (allowlisted); default from `OPENROUTER_MODEL` |

**Response (`SuggestResponse`):**

```json
{
  "section": "experience",
  "itemIndex": 0,
  "message": "assistant reply",
  "proposed": null
}
```

- `proposed` — full JSON of the focused scope (entire resume when `section` is `all`, otherwise the block), **only** when the model offers an applyable edit; otherwise `null`.
- Backend drops no-ops: if `proposed` equals the current JSON → `null`.
- Prompt includes: current JSON for the focus, profile (name/email), career path, vacancy context, history.

Flow: `AiController` → `ResumeAiService` → `OpenRouterClient.chat(system, user)`.

**Vacancy match (`POST /api/ai/resume/match`):**

Requires ATS Screener running (`npm run dev` or `tools/ats-screener` on `:5174` with `GEMINI_API_KEY`).

```json
{ "vacancyContext": "job description text…" }
```

Uses the **saved** resume (JSON → plain text) + vacancy context → ATS `POST /api/analyze` (`mode: full-score`). Response:

```json
{
  "averageScore": 72,
  "platforms": [{ "system": "Workday", "vendor": "…", "overallScore": 70, "passesFilter": true }],
  "suggestions": [{ "summary": "…", "details": ["…"], "impact": "high", "platforms": ["Workday"] }],
  "provider": "gemini-3.5-flash-lite",
  "cached": false,
  "summary": "Average ATS match: 72/100 …"
}
```

Flow: `AtsMatchController` → `AtsMatchService` → `AtsScreenerClient` → `tools/ats-screener`.

### LaTeX (raw) — `/api/latex`

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/latex/compile` | body `{ "source": "..." }` → PDF |

Same `LatexCompileService` as resume compile.

### Vacancies — `/api/vacancies`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/vacancies` | list (newest first) |
| GET | `/api/vacancies/{id}` | detail + requirements + application summary |
| POST | `/api/vacancies` | create (url unique) |
| PUT | `/api/vacancies/{id}` | update |
| DELETE | `/api/vacancies/{id}` | delete |

### Applications — `/api/applications`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/applications` | all applications |
| GET | `/api/applications/{id}` | one |
| POST | `/api/applications` | upsert by vacancy (create or update status/notes) |
| PUT | `/api/applications/{id}` | status + notes |
| DELETE | `/api/applications/{id}` | delete |

When status ≠ `NOT_APPLIED`, `appliedAt` is set if missing.

### CV files — `/api/cvs`, sendings

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/cvs` | CV versions for current user |
| POST | `/api/cvs` | multipart: `label` + `file` → S3 upload + `cv_version` row |
| GET | `/api/cvs/{id}/download` | download file |
| DELETE | `/api/cvs/{id}` | delete (blocked if linked to sendings) |
| GET | `/api/cv-sendings` | all CV sendings |
| GET | `/api/vacancies/{vacancyId}/cv-sendings` | sendings for a vacancy |
| POST | `/api/vacancies/{vacancyId}/cv-sendings` | `{ "cvVersionId", "notes?" }` — record a send |

---

## External integrations

### S3 (`ObjectStorageService`)

- Upload / download / delete by `storage_key`.
- Client built in `S3Config` from `app.s3.*` (path-style for S3Mock).
- S3 failures → `IllegalStateException` → HTTP **502**.

### OpenRouter (`OpenRouterClient`)

- `RestClient` with ~90s timeout, Bearer key, `HTTP-Referer` / `X-Title` headers.
- Strips ``` fences around JSON if the model wraps them anyway.
- Provider HTTP errors are logged and surfaced as runtime failures (**502**).

### Tectonic (`LatexCompileService`)

1. Temp dir → `main.tex`
2. Native `tectonic` if on `PATH` (cache `~/.cache/job-search-tectonic`)
3. Otherwise Docker image
4. 120s timeout; compile failure → **400** with tectonic log; “PDF missing” → **502**

---

## Errors (`ApiExceptionHandler`)

Uniform body:

```json
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "...",
  "fields": { "fieldName": "..." }
}
```

| Exception | HTTP |
|-----------|------|
| `MethodArgumentNotValidException` | 400 (+ `fields`) |
| `IllegalArgumentException` | 400 (incl. bad LaTeX / bad section) |
| `NotFoundException` | 404 |
| `ConflictException` | 409 (duplicate url, CV linked to sendings, …) |
| `IllegalStateException` | 502 (OpenRouter / S3 / PDF missing / config) |
| other | 500 (`Internal server error`) |

---

## Logging

SLF4J on key services:

- save resume / profile
- AI suggest (section, career/vacancy flags, hasProposed)
- OpenRouter request/response errors
- S3 upload/delete / errors
- LaTeX compile success / engine choice
- vacancy & application CRUD
- CV upload / delete / send
- warn/error levels in `ApiExceptionHandler`

---

## MVP limitations

- Session cookie auth + CSRF (`XSRF-TOKEN` / `X-XSRF-TOKEN`). After logout the CSRF cookie may be stale — frontend must `GET /api/auth/csrf` again (done in `api.logout` / `api.refreshCsrf`).
- `POST /api/auth/logout` is `permitAll` (expired sessions can clear state) but still requires a CSRF header.
- Browsers should talk only to the SPA origin (CORS). Auth rate limits key by IP: by default `remoteAddr` only. Set `APP_AUTH_TRUST_FORWARDED_HEADERS=true` behind Vite/nginx and list proxy IPs in `APP_AUTH_TRUSTED_PROXIES` — never enable trust if clients can reach core-api without that proxy (spoofable `X-Forwarded-For`).
- `vacancyContext` for AI is **not stored** on the backend — sent from the client on each suggest.
- Resume JSON is a Postgres string (not `jsonb`); validated via DTO on save.
- No separate AI service: everything lives in `core-api`.
- CORS origins come from `APP_CORS_ALLOWED_ORIGINS` (set prod SPA URL there).

---

## Useful paths

| Path | Purpose |
|------|---------|
| `src/main/resources/application.yml` | datasource, Flyway, app.* |
| `src/main/resources/db/migration/` | schema |
| `src/main/resources/default-resume.json` | starter resume |
| `../.env.example` | env for compose + API |
| `../docker-compose.yml` | Postgres + S3Mock |

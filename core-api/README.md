# core-api

HTTP API for the Job Search platform: vacancies and applications, CV files in S3, structured resume (JSON → LaTeX → PDF), user profile with career path, and AI section edits via OpenRouter.

Listens on `http://localhost:8080` by default. The Vite frontend calls `/api/**` from `http://localhost:5173` (CORS allowed).

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
| `app.current-user-id` / `APP_CURRENT_USER_ID` | Temporary single-user mode: all ops as this `app_user.id` | `1` |
| `app.s3.*` / `S3_*` | Endpoint, region, keys, bucket, path-style | localhost:9090, bucket `job-search-cvs` |
| `app.open-router.api-key` / `OPENROUTER_API_KEY` | OpenRouter API key | empty |
| `app.open-router.model` / `OPENROUTER_MODEL` | Chat model | `openai/gpt-4o-mini` |
| `app.open-router.base-url` | API base URL | `https://openrouter.ai/api/v1` |
| `POSTGRES_*` | JDBC to Postgres | see `application.yml` |

Multipart: max file **10MB**, request **12MB**.

---

## Package layout

```
com.jobsearch.core_api
├── ai/              OpenRouter client + AI suggest for resume sections
├── common/          NotFoundException, ConflictException, ApiExceptionHandler
├── config/          AppProperties, CorsConfig, S3Config
├── cv/              CV file versions + sendings linked to vacancies
├── jobapplication/  Application status (1:1 with vacancy)
├── latex/           Compile arbitrary LaTeX → PDF
├── profile/         app_user: name, email, career_path
├── resume/          Structured resume JSON, LaTeX render, compile
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
| POST | `/api/applications` | create (one per vacancy) |
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

- Single user via `APP_CURRENT_USER_ID`, no login.
- `vacancyContext` for AI is **not stored** on the backend — sent from the client on each suggest.
- Resume JSON is a Postgres string (not `jsonb`); validated via DTO on save.
- No separate AI service: everything lives in `core-api`.
- CORS only for `http://localhost:5173`.

---

## Useful paths

| Path | Purpose |
|------|---------|
| `src/main/resources/application.yml` | datasource, Flyway, app.* |
| `src/main/resources/db/migration/` | schema |
| `src/main/resources/default-resume.json` | starter resume |
| `../.env.example` | env for compose + API |
| `../docker-compose.yml` | Postgres + S3Mock |

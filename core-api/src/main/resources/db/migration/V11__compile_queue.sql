-- Resume PDF compilation queue (drained by CompileDispatcher) and a PDF cache keyed by
-- the sha256 of the LaTeX source. See tools/latex-worker/SPEC.md §4.
CREATE TABLE compile_job (
    id            UUID PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    source_hash   VARCHAR(64)  NOT NULL,
    source        TEXT,
    status        VARCHAR(16)  NOT NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    error_code    VARCHAR(32),
    error_message TEXT,
    lease_until   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    CONSTRAINT ck_compile_job_status CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED'))
);

CREATE INDEX idx_compile_job_queued ON compile_job (created_at) WHERE status = 'QUEUED';
CREATE INDEX idx_compile_job_lease ON compile_job (lease_until) WHERE status = 'RUNNING';
-- At most one active job per user and source, so a double click does not compile twice.
CREATE UNIQUE INDEX uq_compile_job_active
    ON compile_job (user_id, source_hash) WHERE status IN ('QUEUED', 'RUNNING');

CREATE TABLE resume_pdf_cache (
    source_hash   VARCHAR(64) PRIMARY KEY,
    pdf           BYTEA       NOT NULL,
    size_bytes    INT         NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

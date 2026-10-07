-- compile_job becomes the shared queue for every background job type (see core-api/docs/background-jobs.md).
-- Renamed in place, so jobs queued during a deploy keep working.
ALTER TABLE compile_job RENAME TO background_job;
ALTER TABLE background_job RENAME CONSTRAINT compile_job_pkey TO background_job_pkey;
ALTER TABLE background_job RENAME CONSTRAINT compile_job_user_id_fkey TO background_job_user_id_fkey;
ALTER TABLE background_job RENAME CONSTRAINT ck_compile_job_status TO ck_background_job_status;

ALTER TABLE background_job RENAME COLUMN source_hash TO dedupe_key;
ALTER TABLE background_job RENAME COLUMN source TO payload;
-- Every existing row is a resume compile.
ALTER TABLE background_job ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'RESUME_PDF';
ALTER TABLE background_job ALTER COLUMN type DROP DEFAULT;
ALTER TABLE background_job ADD CONSTRAINT ck_background_job_type CHECK (type IN ('RESUME_PDF', 'ATS_MATCH'));
ALTER TABLE background_job ADD COLUMN result TEXT;

-- Claims and the one-active-job rule are now per type.
DROP INDEX idx_compile_job_queued;
CREATE INDEX idx_background_job_queued ON background_job (type, created_at) WHERE status = 'QUEUED';
ALTER INDEX idx_compile_job_lease RENAME TO idx_background_job_lease;
DROP INDEX uq_compile_job_active;
CREATE UNIQUE INDEX uq_background_job_active
    ON background_job (user_id, type, dedupe_key) WHERE status IN ('QUEUED', 'RUNNING');

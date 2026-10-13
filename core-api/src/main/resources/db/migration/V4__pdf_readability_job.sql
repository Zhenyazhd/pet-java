-- A new background job type: checking whether an ATS can read a built resume PDF (tools/ats-worker).
ALTER TABLE background_job DROP CONSTRAINT ck_background_job_type;
ALTER TABLE background_job ADD CONSTRAINT ck_background_job_type
    CHECK (type IN ('RESUME_PDF', 'ATS_MATCH', 'VACANCY_IMPORT', 'PDF_READABILITY'));

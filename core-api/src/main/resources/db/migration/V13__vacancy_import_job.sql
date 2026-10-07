-- Vacancy import (LLM extraction of a pasted posting) runs as a background job.
ALTER TABLE background_job DROP CONSTRAINT ck_background_job_type;
ALTER TABLE background_job ADD CONSTRAINT ck_background_job_type
    CHECK (type IN ('RESUME_PDF', 'ATS_MATCH', 'VACANCY_IMPORT'));

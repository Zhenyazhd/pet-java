-- The application status was a separate table with exactly one row per vacancy (UNIQUE vacancy_id, created with
-- the vacancy). It becomes three columns of vacancy; existing rows are copied before the table is dropped.
ALTER TABLE vacancy
    ADD COLUMN application_status varchar(32) NOT NULL DEFAULT 'NOT_APPLIED',
    ADD COLUMN applied_at timestamp with time zone,
    ADD COLUMN notes text,
    ADD CONSTRAINT ck_vacancy_application_status CHECK (application_status IN
        ('NOT_APPLIED', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN'));

UPDATE vacancy v
SET application_status = a.status, applied_at = a.applied_at, notes = a.notes
FROM job_application a
WHERE a.vacancy_id = v.id;

DROP TABLE job_application;

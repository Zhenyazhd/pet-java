CREATE TABLE vacancy (
    id              BIGSERIAL PRIMARY KEY,
    url             TEXT NOT NULL,
    title           VARCHAR(255) NOT NULL,
    company         VARCHAR(255),
    description     TEXT,
    match_percent   SMALLINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT vacancy_match_percent_range
        CHECK (match_percent IS NULL OR (match_percent >= 0 AND match_percent <= 100))
);

CREATE UNIQUE INDEX uq_vacancy_url ON vacancy (url);

CREATE TABLE vacancy_requirement (
    id              BIGSERIAL PRIMARY KEY,
    vacancy_id      BIGINT NOT NULL REFERENCES vacancy (id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    required        BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_vacancy_requirement UNIQUE (vacancy_id, name)
);

CREATE INDEX idx_vacancy_requirement_vacancy_id ON vacancy_requirement (vacancy_id);

CREATE TABLE job_application (
    id              BIGSERIAL PRIMARY KEY,
    vacancy_id      BIGINT NOT NULL REFERENCES vacancy (id) ON DELETE CASCADE,
    status          VARCHAR(32) NOT NULL,
    applied_at      TIMESTAMPTZ,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_job_application_vacancy UNIQUE (vacancy_id),
    CONSTRAINT job_application_status_check
        CHECK (status IN ('NOT_APPLIED', 'APPLIED', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN'))
);

CREATE INDEX idx_job_application_status ON job_application (status);

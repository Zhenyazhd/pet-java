-- Temporary personal-tool user (auth later)
CREATE TABLE app_user (
    id          BIGINT PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO app_user (id, email, display_name)
VALUES (1, 'me@local', 'Personal User');

CREATE TABLE cv_version (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES app_user (id),
    label           VARCHAR(255) NOT NULL,
    original_filename VARCHAR(512) NOT NULL,
    content_type    VARCHAR(255) NOT NULL,
    storage_key     TEXT NOT NULL,
    size_bytes      BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cv_version_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_cv_version_user_id ON cv_version (user_id);

CREATE TABLE application_cv (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES app_user (id),
    vacancy_id          BIGINT NOT NULL REFERENCES vacancy (id) ON DELETE CASCADE,
    job_application_id  BIGINT REFERENCES job_application (id) ON DELETE SET NULL,
    cv_version_id       BIGINT NOT NULL REFERENCES cv_version (id),
    company             VARCHAR(255),
    notes               TEXT,
    sent_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_application_cv_user_id ON application_cv (user_id);
CREATE INDEX idx_application_cv_vacancy_id ON application_cv (vacancy_id);
CREATE INDEX idx_application_cv_cv_version_id ON application_cv (cv_version_id);

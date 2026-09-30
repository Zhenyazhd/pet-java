-- Per-user vacancies + admin role for invite management
ALTER TABLE app_user
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'USER';

UPDATE app_user SET role = 'ADMIN' WHERE id = 1;

ALTER TABLE vacancy
    ADD COLUMN user_id BIGINT;

UPDATE vacancy SET user_id = 1 WHERE user_id IS NULL;

ALTER TABLE vacancy
    ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE vacancy
    ADD CONSTRAINT fk_vacancy_user FOREIGN KEY (user_id) REFERENCES app_user (id);

DROP INDEX IF EXISTS uq_vacancy_url;
CREATE UNIQUE INDEX uq_vacancy_user_url ON vacancy (user_id, url);
CREATE INDEX idx_vacancy_user_id ON vacancy (user_id);

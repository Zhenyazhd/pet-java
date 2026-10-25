-- Deleting an account removes everything the person owned; vacancy was the one table that blocked it.
ALTER TABLE vacancy DROP CONSTRAINT fk_vacancy_user;
ALTER TABLE vacancy ADD CONSTRAINT fk_vacancy_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE;

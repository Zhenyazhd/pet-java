-- Auth: password hash + unique email + identity sequence for new users
ALTER TABLE app_user
    ADD COLUMN password_hash VARCHAR(255);

ALTER TABLE app_user
    ADD CONSTRAINT uq_app_user_email UNIQUE (email);

CREATE SEQUENCE app_user_id_seq;
SELECT setval('app_user_id_seq', COALESCE((SELECT MAX(id) FROM app_user), 1));
ALTER TABLE app_user ALTER COLUMN id SET DEFAULT nextval('app_user_id_seq');
ALTER SEQUENCE app_user_id_seq OWNED BY app_user.id;

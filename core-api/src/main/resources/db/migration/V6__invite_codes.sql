CREATE TABLE invite_code (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by_user_id BIGINT REFERENCES app_user (id) ON DELETE SET NULL,
    used_at         TIMESTAMPTZ,
    used_by_user_id BIGINT REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT uq_invite_code_code UNIQUE (code)
);

CREATE INDEX idx_invite_code_unused ON invite_code (used_at) WHERE used_at IS NULL;

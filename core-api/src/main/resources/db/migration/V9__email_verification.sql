-- Email verification and password reset, and sign-in with an outside identity provider (Google).
-- Accounts that exist now were let in by an invite and are treated as verified.
ALTER TABLE app_user ADD COLUMN email_verified_at timestamp with time zone;
UPDATE app_user SET email_verified_at = created_at;

-- One live token per user and purpose: issuing a new one replaces the old. Only the sha256 of the token is stored.
CREATE TABLE auth_token (
    user_id bigint NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    purpose character varying(16) NOT NULL CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash character varying(64) NOT NULL UNIQUE,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    PRIMARY KEY (user_id, purpose)
);

-- The provider's stable id of a person (Google's "sub"), never their email, which can change hands.
CREATE TABLE oauth_identity (
    provider character varying(32) NOT NULL,
    subject character varying(255) NOT NULL,
    user_id bigint NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    PRIMARY KEY (provider, subject)
);
CREATE INDEX ix_oauth_identity_user ON oauth_identity (user_id);

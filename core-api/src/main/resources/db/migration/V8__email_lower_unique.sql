-- Normalize emails to lowercase with case-insensitive uniqueness.
-- 1) Resolve any case-only collisions first (keep lowest id; rename the rest)
-- 2) Lowercase all emails
-- 3) Drop the old case-sensitive UNIQUE(email) from V5
-- 4) Enforce uniqueness on lower(email) only

WITH dups AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY lower(email) ORDER BY id) AS rn
    FROM app_user
)
UPDATE app_user u
SET email = 'dup-' || u.id || '-' || lower(u.email)
FROM dups d
WHERE u.id = d.id
  AND d.rn > 1;

UPDATE app_user SET email = lower(email);

ALTER TABLE app_user DROP CONSTRAINT IF EXISTS uq_app_user_email;

DROP INDEX IF EXISTS uq_app_user_email_lower;
CREATE UNIQUE INDEX uq_app_user_email_lower ON app_user (lower(email));

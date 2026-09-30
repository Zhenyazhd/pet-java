-- Optimistic concurrency for resume saves: bumped on every successful PUT /api/resume,
-- so a stale save (e.g. from a second browser tab) is rejected instead of silently
-- overwriting a newer one.
ALTER TABLE app_user ADD COLUMN resume_version INTEGER NOT NULL DEFAULT 0;

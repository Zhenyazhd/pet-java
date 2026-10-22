-- A run is either a full search (fetch from the sources, then rank) or RANK_ONLY (rank the stored, not yet ranked postings;
-- no source and no Apify is called).
ALTER TABLE run ADD COLUMN mode text NOT NULL DEFAULT 'FULL';
ALTER TABLE run ADD CONSTRAINT ck_run_mode CHECK (mode IN ('FULL', 'RANK_ONLY'));

-- The same start log serves both kinds; the limits count them apart (FULL protects Apify spend, RANK_ONLY has its own cap).
ALTER TABLE client_run_start ADD COLUMN mode text NOT NULL DEFAULT 'FULL';
ALTER TABLE client_run_start ADD CONSTRAINT ck_run_start_mode CHECK (mode IN ('FULL', 'RANK_ONLY'));

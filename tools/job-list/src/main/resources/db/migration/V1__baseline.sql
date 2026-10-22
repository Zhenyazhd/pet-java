CREATE TABLE search_profile (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client text NOT NULL,
    owner_ref text NOT NULL,
    name text NOT NULL,
    expectations text NOT NULL,
    roles text[] NOT NULL,
    regions text NOT NULL,
    sources text[] NOT NULL,
    min_score integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uq_search_profile_owner_name UNIQUE (client, owner_ref, name),
    CONSTRAINT ck_search_profile_min_score CHECK (min_score BETWEEN 0 AND 100),
    CONSTRAINT ck_search_profile_sources CHECK (sources <@ ARRAY['LINKEDIN','HELLOWORK','GREENHOUSE','WTTJ'] AND cardinality(sources) > 0),
    CONSTRAINT ck_search_profile_roles CHECK (cardinality(roles) > 0)
);

CREATE TABLE job (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source text NOT NULL,
    external_id text NOT NULL,
    fingerprint text NOT NULL,
    url text NOT NULL,
    title text NOT NULL,
    company text NOT NULL,
    description_text text NOT NULL,
    countries text[] NOT NULL,
    cities text[] NOT NULL,
    remote text NOT NULL,
    contract_type text NOT NULL,
    salary_min numeric,
    salary_max numeric,
    salary_currency text,
    salary_period text,
    skills text[] NOT NULL,
    posted_at timestamptz,
    fetched_at timestamptz NOT NULL,
    CONSTRAINT ck_job_source CHECK (source IN ('LINKEDIN','HELLOWORK','GREENHOUSE','WTTJ')),
    CONSTRAINT ck_job_remote CHECK (remote IN ('ON_SITE','HYBRID','REMOTE','UNKNOWN')),
    CONSTRAINT ck_job_contract CHECK (contract_type IN ('FULL_TIME','PART_TIME','CONTRACT','INTERNSHIP','TEMPORARY','OTHER','UNKNOWN')),
    CONSTRAINT uq_job_source_external UNIQUE (source, external_id),
    CONSTRAINT uq_job_fingerprint UNIQUE (fingerprint)
);
CREATE INDEX idx_job_fetched_at ON job (fetched_at);

CREATE TABLE match (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profile_id bigint NOT NULL REFERENCES search_profile (id) ON DELETE CASCADE,
    job_id bigint NOT NULL REFERENCES job (id) ON DELETE CASCADE,
    score integer NOT NULL,
    reason text NOT NULL,
    prompt_hash text NOT NULL,
    ranked_at timestamptz NOT NULL,
    delivered_at timestamptz,
    CONSTRAINT uq_match_profile_job UNIQUE (profile_id, job_id),
    CONSTRAINT ck_match_score CHECK (score BETWEEN 0 AND 100)
);
CREATE INDEX idx_match_job ON match (job_id);
CREATE INDEX idx_match_profile_score ON match (profile_id, score DESC, id DESC) WHERE delivered_at IS NULL;

CREATE TABLE run (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    profile_id bigint NOT NULL REFERENCES search_profile (id) ON DELETE CASCADE,
    status text NOT NULL,
    created_at timestamptz NOT NULL,
    started_at timestamptz,
    finished_at timestamptz,
    lease_until timestamptz,
    fetched integer NOT NULL DEFAULT 0,
    new_jobs integer NOT NULL DEFAULT 0,
    ranked integer NOT NULL DEFAULT 0,
    cost_usd numeric NOT NULL DEFAULT 0,
    error text,
    CONSTRAINT ck_run_status CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','PARTIAL','FAILED'))
);
CREATE INDEX idx_run_profile_created ON run (profile_id, created_at DESC);
-- At most one unfinished run per profile; a second request gets the existing one.
CREATE UNIQUE INDEX uq_run_active ON run (profile_id) WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX idx_run_status_lease ON run (status, lease_until);

-- What was spent (and what is reserved because a call may be running). The monthly budget, the wait between two fetches of
-- the same search and the lookback window are read from here. It has no foreign keys on purpose: deleting a profile or
-- pruning old runs must not erase the money that was spent. A row is written (RESERVED, at the per-source cap) before
-- Apify is called and settled with the real cost afterwards, so a crash leaves the cost counted.
CREATE TABLE spend_ledger (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    kind text NOT NULL,
    source text,
    query_key text,
    profile_id bigint,
    run_id bigint,
    apify_run_id text,
    status text NOT NULL,
    cost_usd numeric NOT NULL,
    created_at timestamptz NOT NULL,
    settled_at timestamptz,
    CONSTRAINT ck_spend_kind CHECK (kind IN ('APIFY', 'LLM')),
    CONSTRAINT ck_spend_status CHECK (status IN ('RESERVED', 'SUCCEEDED', 'FAILED', 'LOST')),
    CONSTRAINT ck_spend_cost CHECK (cost_usd >= 0)
);
CREATE INDEX idx_spend_kind_created ON spend_ledger (kind, created_at);
CREATE INDEX idx_spend_query ON spend_ledger (source, query_key, created_at);
CREATE INDEX idx_spend_run ON spend_ledger (run_id) WHERE status = 'RESERVED';

-- A job the model could not rank for this profile and prompt: after two failures it is not offered again, so one
-- unrankable posting cannot be paid for on every run (and cannot fill the whole per-run cap).
CREATE TABLE rank_failure (
    profile_id bigint NOT NULL REFERENCES search_profile (id) ON DELETE CASCADE,
    job_id bigint NOT NULL REFERENCES job (id) ON DELETE CASCADE,
    prompt_hash text NOT NULL,
    failures integer NOT NULL,
    CONSTRAINT pk_rank_failure PRIMARY KEY (profile_id, job_id, prompt_hash)
);

-- One row per run that was queued, kept when the profile (and with it the run) is deleted, so that the daily limit on
-- runs cannot be dodged by deleting and creating profiles. No foreign keys, like spend_ledger.
CREATE TABLE client_run_start (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client text NOT NULL,
    owner_ref text NOT NULL,
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_run_start_client ON client_run_start (client, created_at);
CREATE INDEX idx_run_start_owner ON client_run_start (client, owner_ref, created_at);

CREATE TABLE run_source (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id bigint NOT NULL REFERENCES run (id) ON DELETE CASCADE,
    source text NOT NULL,
    status text NOT NULL,
    apify_run_id text,
    fetched integer NOT NULL DEFAULT 0,
    new_jobs integer NOT NULL DEFAULT 0,
    dropped integer NOT NULL DEFAULT 0,
    cost_usd numeric NOT NULL DEFAULT 0,
    started_at timestamptz NOT NULL,
    error text,
    CONSTRAINT ck_run_source_status CHECK (status IN ('SUCCEEDED','FAILED','SKIPPED')),
    CONSTRAINT uq_run_source UNIQUE (run_id, source)
);


CREATE TABLE public.app_user (
    id bigint NOT NULL,
    email character varying(255) NOT NULL,
    display_name character varying(255) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    career_path text DEFAULT ''::text NOT NULL,
    resume_json text DEFAULT ''::text NOT NULL,
    password_hash character varying(255),
    role character varying(32) DEFAULT 'USER'::character varying NOT NULL,
    resume_version integer DEFAULT 0 NOT NULL
);

CREATE SEQUENCE public.app_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.app_user_id_seq OWNED BY public.app_user.id;

CREATE TABLE public.background_job (
    id uuid NOT NULL,
    user_id bigint NOT NULL,
    dedupe_key character varying(64) NOT NULL,
    payload text,
    status character varying(16) NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    error_code character varying(32),
    error_message text,
    lease_until timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    type character varying(32) NOT NULL,
    result text,
    CONSTRAINT ck_background_job_status CHECK (((status)::text = ANY ((ARRAY['QUEUED'::character varying, 'RUNNING'::character varying, 'DONE'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT ck_background_job_type CHECK (((type)::text = ANY ((ARRAY['RESUME_PDF'::character varying, 'ATS_MATCH'::character varying, 'VACANCY_IMPORT'::character varying])::text[])))
);

CREATE TABLE public.invite_code (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    created_by_user_id bigint,
    used_at timestamp with time zone,
    used_by_user_id bigint
);

CREATE SEQUENCE public.invite_code_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.invite_code_id_seq OWNED BY public.invite_code.id;

CREATE TABLE public.job_application (
    id bigint NOT NULL,
    vacancy_id bigint NOT NULL,
    status character varying(32) NOT NULL,
    applied_at timestamp with time zone,
    notes text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT job_application_status_check CHECK (((status)::text = ANY ((ARRAY['NOT_APPLIED'::character varying, 'APPLIED'::character varying, 'INTERVIEW'::character varying, 'OFFER'::character varying, 'REJECTED'::character varying, 'WITHDRAWN'::character varying])::text[])))
);

CREATE SEQUENCE public.job_application_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.job_application_id_seq OWNED BY public.job_application.id;

CREATE TABLE public.resume_pdf_cache (
    source_hash character varying(64) NOT NULL,
    pdf bytea NOT NULL,
    size_bytes integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_used_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.vacancy (
    id bigint NOT NULL,
    url text NOT NULL,
    title character varying(255) NOT NULL,
    company character varying(255),
    description text,
    match_percent smallint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    user_id bigint NOT NULL,
    CONSTRAINT vacancy_match_percent_range CHECK (((match_percent IS NULL) OR ((match_percent >= 0) AND (match_percent <= 100))))
);

CREATE SEQUENCE public.vacancy_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.vacancy_id_seq OWNED BY public.vacancy.id;

CREATE TABLE public.vacancy_requirement (
    id bigint NOT NULL,
    vacancy_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    required boolean DEFAULT true NOT NULL
);

CREATE SEQUENCE public.vacancy_requirement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.vacancy_requirement_id_seq OWNED BY public.vacancy_requirement.id;

ALTER TABLE ONLY public.app_user ALTER COLUMN id SET DEFAULT nextval('public.app_user_id_seq'::regclass);

ALTER TABLE ONLY public.invite_code ALTER COLUMN id SET DEFAULT nextval('public.invite_code_id_seq'::regclass);

ALTER TABLE ONLY public.job_application ALTER COLUMN id SET DEFAULT nextval('public.job_application_id_seq'::regclass);

ALTER TABLE ONLY public.vacancy ALTER COLUMN id SET DEFAULT nextval('public.vacancy_id_seq'::regclass);

ALTER TABLE ONLY public.vacancy_requirement ALTER COLUMN id SET DEFAULT nextval('public.vacancy_requirement_id_seq'::regclass);

ALTER TABLE ONLY public.app_user
    ADD CONSTRAINT app_user_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.background_job
    ADD CONSTRAINT background_job_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.invite_code
    ADD CONSTRAINT invite_code_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.job_application
    ADD CONSTRAINT job_application_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.resume_pdf_cache
    ADD CONSTRAINT resume_pdf_cache_pkey PRIMARY KEY (source_hash);

ALTER TABLE ONLY public.invite_code
    ADD CONSTRAINT uq_invite_code_code UNIQUE (code);

ALTER TABLE ONLY public.job_application
    ADD CONSTRAINT uq_job_application_vacancy UNIQUE (vacancy_id);

ALTER TABLE ONLY public.vacancy_requirement
    ADD CONSTRAINT uq_vacancy_requirement UNIQUE (vacancy_id, name);

ALTER TABLE ONLY public.vacancy
    ADD CONSTRAINT vacancy_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.vacancy_requirement
    ADD CONSTRAINT vacancy_requirement_pkey PRIMARY KEY (id);

CREATE INDEX idx_background_job_lease ON public.background_job USING btree (lease_until) WHERE ((status)::text = 'RUNNING'::text);

CREATE INDEX idx_background_job_queued ON public.background_job USING btree (type, created_at) WHERE ((status)::text = 'QUEUED'::text);

CREATE INDEX idx_invite_code_unused ON public.invite_code USING btree (used_at) WHERE (used_at IS NULL);

CREATE INDEX idx_job_application_status ON public.job_application USING btree (status);

CREATE INDEX idx_vacancy_requirement_vacancy_id ON public.vacancy_requirement USING btree (vacancy_id);

CREATE INDEX idx_vacancy_user_id ON public.vacancy USING btree (user_id);

CREATE UNIQUE INDEX uq_app_user_email_lower ON public.app_user USING btree (lower((email)::text));

CREATE UNIQUE INDEX uq_background_job_active ON public.background_job USING btree (user_id, type, dedupe_key) WHERE ((status)::text = ANY ((ARRAY['QUEUED'::character varying, 'RUNNING'::character varying])::text[]));

CREATE UNIQUE INDEX uq_vacancy_user_url ON public.vacancy USING btree (user_id, url);

ALTER TABLE ONLY public.background_job
    ADD CONSTRAINT background_job_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.app_user(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.vacancy
    ADD CONSTRAINT fk_vacancy_user FOREIGN KEY (user_id) REFERENCES public.app_user(id);

ALTER TABLE ONLY public.invite_code
    ADD CONSTRAINT invite_code_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.app_user(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.invite_code
    ADD CONSTRAINT invite_code_used_by_user_id_fkey FOREIGN KEY (used_by_user_id) REFERENCES public.app_user(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.job_application
    ADD CONSTRAINT job_application_vacancy_id_fkey FOREIGN KEY (vacancy_id) REFERENCES public.vacancy(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.vacancy_requirement
    ADD CONSTRAINT vacancy_requirement_vacancy_id_fkey FOREIGN KEY (vacancy_id) REFERENCES public.vacancy(id) ON DELETE CASCADE;


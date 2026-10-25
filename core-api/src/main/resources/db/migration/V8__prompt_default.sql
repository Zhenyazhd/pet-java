-- The wording an admin has made the default for every user, per editable part of the AI chat prompt (PromptPart.java).
-- A row exists only for a part whose default differs from the text shipped on the classpath.
CREATE TABLE prompt_default (
    part_key character varying(40) PRIMARY KEY,
    content text NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);

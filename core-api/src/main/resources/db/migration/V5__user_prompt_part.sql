-- A user's own wording for one editable part of the AI chat prompt (see PromptPart.java). A row exists only for a
-- customised part; the defaults live on the classpath (src/main/resources/prompts).
-- base_hash is the sha256 (hex) of the default text the user started from: when the default is edited later it no
-- longer matches, and the API reports defaultChanged so the user can look at the new default.
CREATE TABLE user_prompt_part (
    user_id bigint NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    part_key character varying(40) NOT NULL,
    content text NOT NULL,
    base_hash character varying(64) NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    PRIMARY KEY (user_id, part_key)
);

-- One row per distinct skill / requirement; vacancies point at it instead of repeating the text.
-- name_key is the normalised form (see SkillKey.java: NFKC, whitespace collapsed and trimmed, lower case) and is
-- what makes two spellings the same skill; name keeps the spelling seen first.
CREATE TABLE skill (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name character varying(255) NOT NULL,
    name_key text NOT NULL,
    CONSTRAINT uq_skill_name_key UNIQUE (name_key)
);

-- The same normalisation as SkillKey, applied to the rows that exist today.
CREATE TEMPORARY TABLE tmp_requirement_key ON COMMIT DROP AS
SELECT id,
       btrim(regexp_replace(normalize(name, NFKC), '\s+', ' ', 'g')) AS clean_name,
       lower(btrim(regexp_replace(normalize(name, NFKC), '\s+', ' ', 'g'))) AS name_key
FROM vacancy_requirement;

DELETE FROM tmp_requirement_key WHERE name_key = '';

INSERT INTO skill (name, name_key)
SELECT DISTINCT ON (name_key) left(clean_name, 255), name_key
FROM tmp_requirement_key
ORDER BY name_key, id;

ALTER TABLE vacancy_requirement ADD COLUMN skill_id bigint;

UPDATE vacancy_requirement r
SET skill_id = s.id
FROM tmp_requirement_key k
JOIN skill s ON s.name_key = k.name_key
WHERE r.id = k.id;

-- Rows whose name was blank have no skill.
DELETE FROM vacancy_requirement WHERE skill_id IS NULL;

-- Two spellings of one skill on the same vacancy ("Java" and "java ") now point at the same skill: keep the first
-- row and mark it required if any of them was.
UPDATE vacancy_requirement r
SET required = d.any_required
FROM (
    SELECT min(id) AS keep_id, bool_or(required) AS any_required
    FROM vacancy_requirement
    GROUP BY vacancy_id, skill_id
) d
WHERE r.id = d.keep_id AND r.required <> d.any_required;

DELETE FROM vacancy_requirement r
USING vacancy_requirement keep
WHERE keep.vacancy_id = r.vacancy_id AND keep.skill_id = r.skill_id AND keep.id < r.id;

ALTER TABLE vacancy_requirement ALTER COLUMN skill_id SET NOT NULL;
ALTER TABLE vacancy_requirement DROP CONSTRAINT uq_vacancy_requirement;
ALTER TABLE vacancy_requirement DROP COLUMN name;
-- The unique constraint also serves lookups by vacancy_id, so the separate index is no longer needed.
DROP INDEX idx_vacancy_requirement_vacancy_id;
ALTER TABLE vacancy_requirement
    ADD CONSTRAINT uq_vacancy_requirement_skill UNIQUE (vacancy_id, skill_id);
ALTER TABLE vacancy_requirement
    ADD CONSTRAINT fk_vacancy_requirement_skill FOREIGN KEY (skill_id) REFERENCES skill(id);

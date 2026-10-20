ALTER TABLE run_source DROP CONSTRAINT ck_run_source_status;
ALTER TABLE run_source ADD CONSTRAINT ck_run_source_status CHECK (status IN ('RUNNING','SUCCEEDED','FAILED','SKIPPED'));

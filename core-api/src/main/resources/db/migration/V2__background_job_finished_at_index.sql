-- JobRepository.deleteFinishedBefore runs every 30 s; without an index it scans all jobs each time.
CREATE INDEX idx_background_job_finished ON background_job (finished_at)
    WHERE status IN ('DONE', 'FAILED');

ALTER TABLE sys_ehr_sync_issue
    ADD COLUMN failure_stage VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN';

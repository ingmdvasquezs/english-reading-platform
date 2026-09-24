CREATE TABLE import_jobs (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES imported_documents(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    source_asset_key VARCHAR(500) NOT NULL,
    language_override VARCHAR(50),
    worker_id VARCHAR(100),
    lease_token UUID,
    lease_until TIMESTAMP,
    heartbeat_at TIMESTAMP,
    next_attempt_at TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    last_error_code VARCHAR(50),
    last_error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_import_jobs_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'ABORTED'))
);

CREATE UNIQUE INDEX uk_import_jobs_active_document
    ON import_jobs (document_id)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE INDEX idx_import_jobs_claim
    ON import_jobs (status, next_attempt_at, lease_until)
    WHERE status IN ('PENDING', 'PROCESSING');

CREATE INDEX idx_import_jobs_user
    ON import_jobs (user_id, created_at DESC);

ALTER TABLE document_uploads
    ADD CONSTRAINT fk_document_uploads_confirmed_job
        FOREIGN KEY (confirmed_job_id) REFERENCES import_jobs(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uk_document_uploads_confirmed_job
    ON document_uploads (confirmed_job_id)
    WHERE confirmed_job_id IS NOT NULL;

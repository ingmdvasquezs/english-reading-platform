CREATE TABLE document_uploads (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    original_filename VARCHAR(500) NOT NULL,
    format VARCHAR(20) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    expected_size_bytes BIGINT NOT NULL,
    expected_checksum_sha256 VARCHAR(64),
    storage_key VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP,
    confirmed_job_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_document_uploads_status CHECK (status IN ('PENDING', 'CONFIRMED', 'EXPIRED', 'ABORTED')),
    CONSTRAINT ck_document_uploads_format CHECK (format IN ('EPUB', 'PDF')),
    CONSTRAINT uk_document_uploads_document_id UNIQUE (document_id)
);

CREATE INDEX idx_document_uploads_user_status
    ON document_uploads (user_id, status);

CREATE INDEX idx_document_uploads_expires
    ON document_uploads (status, expires_at)
    WHERE status = 'PENDING';

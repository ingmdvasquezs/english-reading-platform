ALTER TABLE imported_documents
    ADD COLUMN failure_reason VARCHAR(50);

ALTER TABLE imported_documents
    ADD CONSTRAINT ck_imported_documents_failure_reason CHECK (
        failure_reason IS NULL OR failure_reason IN (
            'INVALID_EPUB',
            'UNSUPPORTED_DRM',
            'LANGUAGE_REQUIRED',
            'UNSUPPORTED_LANGUAGE',
            'FILE_TOO_LARGE',
            'SECURITY_LIMIT_EXCEEDED',
            'STORAGE_FAILURE',
            'IMPORT_FAILURE'
        )
    );

ALTER TABLE imported_documents
    ADD CONSTRAINT ck_imported_documents_failure_state CHECK (
        (import_status = 'FAILED' AND failure_reason IS NOT NULL)
        OR (import_status <> 'FAILED' AND failure_reason IS NULL)
    );

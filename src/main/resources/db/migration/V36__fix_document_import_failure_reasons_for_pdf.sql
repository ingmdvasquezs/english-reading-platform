ALTER TABLE imported_documents
    DROP CONSTRAINT ck_imported_documents_failure_reason;

ALTER TABLE imported_documents
    ADD CONSTRAINT ck_imported_documents_failure_reason CHECK (
        failure_reason IS NULL OR failure_reason IN (
            'INVALID_EPUB',
            'INVALID_PDF',
            'PDF_PASSWORD_PROTECTED',
            'PDF_SCANNED_NOT_SUPPORTED',
            'UNSUPPORTED_DRM',
            'LANGUAGE_REQUIRED',
            'UNSUPPORTED_LANGUAGE',
            'FILE_TOO_LARGE',
            'SECURITY_LIMIT_EXCEEDED',
            'STORAGE_FAILURE',
            'IMPORT_FAILURE'
        )
    );

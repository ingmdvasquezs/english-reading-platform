-- Add explicit storage provider support across document uploads, import jobs, and imported documents.
-- Existing rows are backfilled as 'FILESYSTEM'.
-- No default is kept so new code must explicitly declare the provider ('FILESYSTEM' or 'S3').

ALTER TABLE document_uploads
    ADD COLUMN storage_provider VARCHAR(20);

ALTER TABLE import_jobs
    ADD COLUMN storage_provider VARCHAR(20);

ALTER TABLE imported_documents
    ADD COLUMN source_storage_provider VARCHAR(20);

UPDATE document_uploads
SET storage_provider = 'FILESYSTEM'
WHERE storage_provider IS NULL;

UPDATE import_jobs
SET storage_provider = 'FILESYSTEM'
WHERE storage_provider IS NULL;

UPDATE imported_documents
SET source_storage_provider = 'FILESYSTEM'
WHERE source_storage_provider IS NULL;

ALTER TABLE document_uploads
    ALTER COLUMN storage_provider SET NOT NULL,
    ADD CONSTRAINT ck_document_uploads_storage_provider
        CHECK (storage_provider IN ('FILESYSTEM', 'S3'));

ALTER TABLE import_jobs
    ALTER COLUMN storage_provider SET NOT NULL,
    ADD CONSTRAINT ck_import_jobs_storage_provider
        CHECK (storage_provider IN ('FILESYSTEM', 'S3'));

ALTER TABLE imported_documents
    ALTER COLUMN source_storage_provider SET NOT NULL,
    ADD CONSTRAINT ck_imported_documents_source_storage_provider
        CHECK (source_storage_provider IN ('FILESYSTEM', 'S3'));

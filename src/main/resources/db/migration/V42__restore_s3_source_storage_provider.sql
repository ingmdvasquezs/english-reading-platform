-- =============================================================================
-- V42: Restore source_storage_provider for documents imported through S3
--
-- Before this release the READY / FAILED transitions rebuilt imported_documents
-- with a constructor that always set source_storage_provider = 'FILESYSTEM',
-- even when the source object lived in S3 (BACK-010).
--
-- Only rows backed by unambiguous S3 evidence are corrected:
--   * the document is currently marked FILESYSTEM;
--   * at least one import job for the document has storage_provider = 'S3'
--     and points at the same source_asset_key as the document;
--   * no import job for the document was a FILESYSTEM job.
-- The legacy multipart (FILESYSTEM) pipeline never creates import_jobs rows,
-- so genuinely local documents are never matched. updated_at is not touched:
-- this is a metadata correction, not a user-visible change. Re-running the
-- statement is a no-op.
-- =============================================================================

UPDATE imported_documents d
SET source_storage_provider = 'S3'
WHERE d.source_storage_provider = 'FILESYSTEM'
  AND d.source_asset_key IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM import_jobs j
      WHERE j.document_id = d.id
        AND j.storage_provider = 'S3'
        AND j.source_asset_key = d.source_asset_key
  )
  AND NOT EXISTS (
      SELECT 1
      FROM import_jobs j
      WHERE j.document_id = d.id
        AND j.storage_provider <> 'S3'
  );

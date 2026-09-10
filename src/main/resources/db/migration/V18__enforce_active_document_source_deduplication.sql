ALTER TABLE imported_documents
    ADD COLUMN deduplication_sha256 CHAR(64);

-- Preserve every historical row. For pre-existing active duplicates, only the oldest
-- canonical row claims the hash; the remaining rows are grandfathered with NULL.
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, source_sha256
               ORDER BY CASE import_status WHEN 'READY' THEN 0 ELSE 1 END,
                        created_at,
                        id
           ) AS position
    FROM imported_documents
    WHERE import_status IN ('PROCESSING', 'READY')
)
UPDATE imported_documents document
SET deduplication_sha256 = document.source_sha256
FROM ranked
WHERE document.id = ranked.id
  AND ranked.position = 1;

ALTER TABLE imported_documents
    ADD CONSTRAINT ck_imported_documents_deduplication_sha256
        CHECK (
            deduplication_sha256 IS NULL
            OR (
                import_status IN ('PROCESSING', 'READY')
                AND deduplication_sha256 = source_sha256
            )
        ),
    ADD CONSTRAINT uk_imported_documents_user_active_source
        UNIQUE (user_id, deduplication_sha256);

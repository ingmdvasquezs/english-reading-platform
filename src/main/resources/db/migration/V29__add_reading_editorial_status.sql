ALTER TABLE readings
    ADD COLUMN editorial_status VARCHAR(20);

UPDATE readings
   SET editorial_status = 'PUBLISHED'
 WHERE origin = 'PLATFORM';

ALTER TABLE readings
    ADD CONSTRAINT ck_readings_editorial_status
        CHECK (editorial_status IS NULL OR editorial_status IN ('PUBLISHED', 'ARCHIVED', 'DRAFT'));

ALTER TABLE readings DROP CONSTRAINT ck_readings_origin_ownership_metadata;

ALTER TABLE readings
    ADD CONSTRAINT ck_readings_origin_ownership_metadata
        CHECK (
            (origin = 'USER' AND user_id IS NOT NULL
                AND editorial_level IS NULL AND category IS NULL AND editorial_status IS NULL)
            OR
            (origin = 'PLATFORM' AND user_id IS NULL
                AND editorial_level IS NOT NULL AND category IS NOT NULL
                AND length(trim(category)) > 0 AND editorial_status IS NOT NULL)
        );

DROP INDEX IF EXISTS idx_readings_platform_created_at_desc;

CREATE INDEX idx_readings_platform_published_created_at_desc
    ON readings (created_at DESC, id)
    WHERE origin = 'PLATFORM' AND editorial_status = 'PUBLISHED';

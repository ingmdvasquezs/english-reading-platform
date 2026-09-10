ALTER TABLE reading_progress
    ADD COLUMN current_part_ordinal INTEGER NULL,
    ADD COLUMN pagination_version INTEGER NULL;

ALTER TABLE reading_progress
    ADD CONSTRAINT ck_reading_progress_part_pair
        CHECK ((current_part_ordinal IS NULL) = (pagination_version IS NULL)),
    ADD CONSTRAINT ck_reading_progress_part_ordinal_positive
        CHECK (current_part_ordinal IS NULL OR current_part_ordinal >= 1),
    ADD CONSTRAINT ck_reading_progress_pagination_version_positive
        CHECK (pagination_version IS NULL OR pagination_version >= 1);

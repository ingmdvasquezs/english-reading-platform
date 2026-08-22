ALTER TABLE user_vocabulary
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_readings_user_created_at_desc
    ON readings (user_id, created_at DESC);

CREATE INDEX idx_user_vocabulary_user_first_seen_at_desc
    ON user_vocabulary (user_id, first_seen_at DESC);

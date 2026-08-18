ALTER TABLE words
    DROP CONSTRAINT uk_words_normalized_value;

ALTER TABLE words
    ADD CONSTRAINT uk_words_normalized_value_language
        UNIQUE (normalized_value, language);

CREATE INDEX idx_readings_user_id
    ON readings (user_id);

CREATE INDEX idx_user_vocabulary_word_id
    ON user_vocabulary (word_id);

CREATE TABLE reading_word_frequencies (
    reading_id UUID NOT NULL REFERENCES readings(id) ON DELETE CASCADE,
    language VARCHAR(10) NOT NULL,
    normalized_value VARCHAR(100) NOT NULL,
    occurrence_count INTEGER NOT NULL CHECK (occurrence_count > 0),
    PRIMARY KEY (reading_id, language, normalized_value)
);

CREATE INDEX idx_rwf_lang_norm ON reading_word_frequencies(language, normalized_value);

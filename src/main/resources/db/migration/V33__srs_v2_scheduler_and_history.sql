-- V33: SRS V2 Core Scheduler (Canonical FSRS-4.5) and Review History

-- Step 1: Add SRS V2 columns to user_vocabulary
ALTER TABLE user_vocabulary
    ADD COLUMN srs_state VARCHAR(20) NOT NULL DEFAULT 'NEW',
    ADD COLUMN stability NUMERIC(10,4) NOT NULL DEFAULT 0.0000,
    ADD COLUMN difficulty NUMERIC(10,4) NOT NULL DEFAULT 5.0000,
    ADD COLUMN repetitions INT NOT NULL DEFAULT 0,
    ADD COLUMN lapses INT NOT NULL DEFAULT 0;

ALTER TABLE user_vocabulary
    ADD CONSTRAINT chk_user_vocabulary_srs_state
        CHECK (srs_state IN ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING')),
    ADD CONSTRAINT chk_user_vocabulary_difficulty
        CHECK (difficulty >= 1.0 AND difficulty <= 10.0),
    ADD CONSTRAINT chk_user_vocabulary_stability
        CHECK (stability >= 0.0),
    ADD CONSTRAINT chk_user_vocabulary_repetitions
        CHECK (repetitions >= 0),
    ADD CONSTRAINT chk_user_vocabulary_lapses
        CHECK (lapses >= 0);

-- Step 2: Backfill existing vocabulary data preserving review scheduling and status
UPDATE user_vocabulary
SET srs_state = 'NEW',
    stability = 0.0000,
    difficulty = 5.0000,
    repetitions = 0,
    lapses = 0
WHERE status IN ('NEW', 'IGNORED');

UPDATE user_vocabulary
SET srs_state = 'LEARNING',
    stability = 0.4872,
    difficulty = 7.6214,
    repetitions = 0,
    lapses = 0
WHERE status = 'LEARNING';

UPDATE user_vocabulary
SET srs_state = 'REVIEW',
    difficulty = 3.9320,
    repetitions = GREATEST(1, review_stage),
    lapses = 0,
    stability = CASE review_stage
        WHEN 0 THEN 1.4003
        WHEN 1 THEN 3.7145
        WHEN 2 THEN 7.0000
        WHEN 3 THEN 13.8206
        WHEN 4 THEN 30.0000
        WHEN 5 THEN 30.0000
        ELSE 13.8206
    END
WHERE status = 'KNOWN';

-- Step 3: Create append-only review history table
CREATE TABLE user_vocabulary_review_history (
    id UUID PRIMARY KEY,
    user_vocabulary_id UUID NOT NULL,
    user_id UUID NOT NULL,
    reviewed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    rating VARCHAR(20) NOT NULL,
    previous_srs_state VARCHAR(20) NOT NULL,
    new_srs_state VARCHAR(20) NOT NULL,
    previous_interval_seconds BIGINT NOT NULL,
    new_interval_seconds BIGINT NOT NULL,
    previous_stability NUMERIC(10,4) NOT NULL,
    new_stability NUMERIC(10,4) NOT NULL,
    previous_difficulty NUMERIC(10,4) NOT NULL,
    new_difficulty NUMERIC(10,4) NOT NULL,
    elapsed_days NUMERIC(10,4) NOT NULL,
    scheduled_days NUMERIC(10,4) NOT NULL,
    CONSTRAINT fk_uv_review_history_user_vocab
        FOREIGN KEY (user_vocabulary_id) REFERENCES user_vocabulary(id) ON DELETE CASCADE,
    CONSTRAINT fk_uv_review_history_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_uv_review_history_rating
        CHECK (rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY'))
);

CREATE INDEX idx_uv_review_history_user_vocab
    ON user_vocabulary_review_history (user_vocabulary_id, reviewed_at DESC);

CREATE INDEX idx_uv_review_history_user
    ON user_vocabulary_review_history (user_id, reviewed_at DESC);

CREATE INDEX idx_user_vocabulary_srs_due
    ON user_vocabulary (user_id, srs_state, next_review_at);
-- V20: Vocabulary review state and English regional language consolidation

-- Step 1: Consolidate regional English words into canonical 'en'
DO $$
DECLARE
    r_word RECORD;
    v_canonical_id UUID;
    v_regional_count INT;
    r_uv RECORD;
    v_canon_uv RECORD;
    v_merged_status VARCHAR(20);
    v_merged_first_seen TIMESTAMP;
    v_merged_learned_at TIMESTAMP;
BEGIN
    FOR r_word IN
        SELECT id, normalized_value, language, created_at
        FROM words
        WHERE LOWER(language) != 'en'
          AND (LOWER(language) LIKE 'en-%' OR LOWER(language) LIKE 'en_%')
    LOOP
        SELECT id INTO v_canonical_id
        FROM words
        WHERE normalized_value = r_word.normalized_value AND language = 'en';

        IF v_canonical_id IS NULL THEN
            UPDATE words SET language = 'en' WHERE id = r_word.id;
        ELSE
            -- Process all user_vocabulary rows pointing to this regional word across all users
            FOR r_uv IN
                SELECT * FROM user_vocabulary WHERE word_id = r_word.id
            LOOP
                SELECT * INTO v_canon_uv
                FROM user_vocabulary
                WHERE user_id = r_uv.user_id AND word_id = v_canonical_id;

                IF v_canon_uv.id IS NOT NULL THEN
                    -- Merge both entries for the same user
                    v_merged_first_seen := LEAST(v_canon_uv.first_seen_at, r_uv.first_seen_at);

                    IF v_canon_uv.status = r_uv.status THEN
                        v_merged_status := v_canon_uv.status;
                    ELSIF (v_canon_uv.status = 'KNOWN' AND r_uv.status = 'LEARNING')
                       OR (v_canon_uv.status = 'LEARNING' AND r_uv.status = 'KNOWN') THEN
                        IF v_canon_uv.status = 'KNOWN' THEN
                            IF v_canon_uv.learned_at IS NOT NULL AND v_canon_uv.learned_at > r_uv.first_seen_at THEN
                                v_merged_status := 'KNOWN';
                            ELSE
                                v_merged_status := 'LEARNING';
                            END IF;
                        ELSE
                            IF r_uv.learned_at IS NOT NULL AND r_uv.learned_at > v_canon_uv.first_seen_at THEN
                                v_merged_status := 'KNOWN';
                            ELSE
                                v_merged_status := 'LEARNING';
                            END IF;
                        END IF;
                    ELSIF (v_canon_uv.status = 'LEARNING' AND r_uv.status = 'NEW')
                       OR (v_canon_uv.status = 'NEW' AND r_uv.status = 'LEARNING') THEN
                        v_merged_status := 'LEARNING';
                    ELSIF (v_canon_uv.status = 'KNOWN' AND r_uv.status = 'NEW')
                       OR (v_canon_uv.status = 'NEW' AND r_uv.status = 'KNOWN') THEN
                        v_merged_status := 'KNOWN';
                    ELSIF (v_canon_uv.status = 'IGNORED' AND r_uv.status = 'LEARNING')
                       OR (v_canon_uv.status = 'LEARNING' AND r_uv.status = 'IGNORED') THEN
                        v_merged_status := 'LEARNING';
                    ELSIF (v_canon_uv.status = 'IGNORED' AND r_uv.status = 'KNOWN')
                       OR (v_canon_uv.status = 'KNOWN' AND r_uv.status = 'IGNORED') THEN
                        v_merged_status := 'KNOWN';
                    ELSIF (v_canon_uv.status = 'IGNORED' AND r_uv.status = 'NEW')
                       OR (v_canon_uv.status = 'NEW' AND r_uv.status = 'IGNORED') THEN
                        v_merged_status := 'IGNORED';
                    ELSE
                        v_merged_status := v_canon_uv.status;
                    END IF;

                    -- Hierarchy for learned_at:
                    -- 1. Valid existing learnedAt of the selected KNOWN record
                    -- 2. Other valid learnedAt among merged entries
                    -- 3. Historical firstSeenAt
                    -- 4. UTC now as defensive last fallback
                    IF v_merged_status = 'KNOWN' THEN
                        IF v_canon_uv.status = 'KNOWN' AND v_canon_uv.learned_at IS NOT NULL THEN
                            v_merged_learned_at := v_canon_uv.learned_at;
                        ELSIF r_uv.status = 'KNOWN' AND r_uv.learned_at IS NOT NULL THEN
                            v_merged_learned_at := r_uv.learned_at;
                        ELSIF v_canon_uv.learned_at IS NOT NULL THEN
                            v_merged_learned_at := v_canon_uv.learned_at;
                        ELSIF r_uv.learned_at IS NOT NULL THEN
                            v_merged_learned_at := r_uv.learned_at;
                        ELSIF v_merged_first_seen IS NOT NULL THEN
                            v_merged_learned_at := v_merged_first_seen;
                        ELSE
                            v_merged_learned_at := (CURRENT_TIMESTAMP AT TIME ZONE 'UTC');
                        END IF;
                    ELSE
                        v_merged_learned_at := NULL;
                    END IF;

                    UPDATE user_vocabulary
                    SET status = v_merged_status,
                        first_seen_at = v_merged_first_seen,
                        learned_at = v_merged_learned_at,
                        version = version + 1
                    WHERE id = v_canon_uv.id;

                    DELETE FROM user_vocabulary WHERE id = r_uv.id;
                ELSE
                    -- User only has the regional entry: reassign to canonical word
                    UPDATE user_vocabulary
                    SET word_id = v_canonical_id
                    WHERE id = r_uv.id;
                END IF;
            END LOOP;

            -- Verify no user_vocabulary rows remain pointing to the regional word
            SELECT COUNT(*) INTO v_regional_count FROM user_vocabulary WHERE word_id = r_word.id;
            IF v_regional_count > 0 THEN
                RAISE EXCEPTION 'Failed to migrate all user_vocabulary rows for word % (%)', r_word.normalized_value, r_word.id;
            END IF;

            -- Now safely delete the regional word
            DELETE FROM words WHERE id = r_word.id;
        END IF;
    END LOOP;
END $$;

-- Step 2: Add review state columns
ALTER TABLE user_vocabulary
    ADD COLUMN review_stage INT NOT NULL DEFAULT 0,
    ADD COLUMN last_reviewed_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN next_review_at TIMESTAMP WITHOUT TIME ZONE;

ALTER TABLE user_vocabulary
    ADD CONSTRAINT chk_user_vocabulary_review_stage
        CHECK (review_stage >= 0 AND review_stage <= 5);

-- Step 3: Backfill review state in UTC
UPDATE user_vocabulary
SET review_stage = 0,
    last_reviewed_at = NULL,
    next_review_at = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
WHERE status = 'LEARNING';

UPDATE user_vocabulary
SET review_stage = 1,
    last_reviewed_at = learned_at,
    next_review_at = NULL
WHERE status = 'KNOWN';

UPDATE user_vocabulary
SET review_stage = 0,
    last_reviewed_at = NULL,
    next_review_at = NULL
WHERE status IN ('NEW', 'IGNORED');

-- Step 4: Index for prioritized due review queries
CREATE INDEX idx_user_vocabulary_due_review
    ON user_vocabulary (user_id, next_review_at)
    WHERE next_review_at IS NOT NULL;

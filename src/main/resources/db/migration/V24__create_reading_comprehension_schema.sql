CREATE TABLE user_comprehension_attempts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reading_id UUID NOT NULL REFERENCES readings(id) ON DELETE CASCADE,
    submission_id UUID NOT NULL,
    score_percentage DECIMAL(5,2) NOT NULL,
    correct_answers_count INT NOT NULL,
    total_questions_count INT NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_comprehension_attempts_user_reading_submission UNIQUE (user_id, reading_id, submission_id)
);

CREATE TABLE reading_comprehension_questions (
    id UUID PRIMARY KEY,
    reading_id UUID NOT NULL REFERENCES readings(id) ON DELETE CASCADE,
    ordinal INT NOT NULL,
    question_type VARCHAR(30) NOT NULL,
    prompt TEXT NOT NULL,
    explanation TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_rc_questions_reading_ordinal UNIQUE (reading_id, ordinal),
    CONSTRAINT ck_rc_question_type CHECK (question_type IN ('FACTUAL', 'INFERENCE', 'MAIN_IDEA'))
);

CREATE TABLE reading_comprehension_options (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES reading_comprehension_questions(id) ON DELETE CASCADE,
    ordinal INT NOT NULL,
    content TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    CONSTRAINT uk_rc_options_question_ordinal UNIQUE (question_id, ordinal)
);

CREATE UNIQUE INDEX uk_rc_options_single_correct
    ON reading_comprehension_options (question_id)
    WHERE is_correct = true;

CREATE TABLE user_comprehension_answers (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES user_comprehension_attempts(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES reading_comprehension_questions(id),
    selected_option_id UUID NOT NULL REFERENCES reading_comprehension_options(id),
    is_correct BOOLEAN NOT NULL,
    CONSTRAINT uk_user_comprehension_answers_attempt_question UNIQUE (attempt_id, question_id)
);

CREATE INDEX idx_user_comprehension_attempts_lookup ON user_comprehension_attempts(user_id, reading_id, submitted_at DESC, id DESC);
CREATE INDEX idx_rc_questions_reading_id ON reading_comprehension_questions(reading_id);
CREATE INDEX idx_rc_options_question_id ON reading_comprehension_options(question_id);
CREATE INDEX idx_user_comprehension_answers_attempt_id ON user_comprehension_answers(attempt_id);
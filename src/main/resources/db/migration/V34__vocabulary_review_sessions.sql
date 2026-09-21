-- V34: Daily Vocabulary Review Sessions and Stable Queue Order
CREATE TABLE vocabulary_review_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    local_review_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    daily_limit INT NOT NULL DEFAULT 15,
    next_queue_sequence BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_review_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uq_review_sessions_user_date
        UNIQUE (user_id, local_review_date),
    CONSTRAINT chk_review_sessions_status
        CHECK (status IN ('ACTIVE', 'COMPLETED')),
    CONSTRAINT chk_review_sessions_daily_limit
        CHECK (daily_limit > 0),
    CONSTRAINT chk_review_sessions_next_queue_sequence
        CHECK (next_queue_sequence >= 1)
);

CREATE TABLE vocabulary_review_session_items (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    user_vocabulary_id UUID NOT NULL,
    base_order INT NOT NULL,
    introduced_at TIMESTAMP WITHOUT TIME ZONE,
    pending_queue_sequence BIGINT,
    CONSTRAINT fk_session_items_session
        FOREIGN KEY (session_id) REFERENCES vocabulary_review_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_session_items_user_vocab
        FOREIGN KEY (user_vocabulary_id) REFERENCES user_vocabulary(id) ON DELETE CASCADE,
    CONSTRAINT uq_session_items_session_vocab
        UNIQUE (session_id, user_vocabulary_id),
    CONSTRAINT uq_session_items_session_order
        UNIQUE (session_id, base_order),
    CONSTRAINT chk_session_items_base_order
        CHECK (base_order >= 1),
    CONSTRAINT chk_session_items_pending_queue_sequence
        CHECK (pending_queue_sequence IS NULL OR pending_queue_sequence >= 1)
);

CREATE INDEX idx_review_sessions_user_date
    ON vocabulary_review_sessions (user_id, local_review_date);

CREATE INDEX idx_review_session_items_session_base
    ON vocabulary_review_session_items (session_id, base_order);

CREATE INDEX idx_review_session_items_pending_queue
    ON vocabulary_review_session_items (session_id, pending_queue_sequence);

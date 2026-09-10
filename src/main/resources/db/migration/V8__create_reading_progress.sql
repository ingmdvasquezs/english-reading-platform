CREATE TABLE reading_progress (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reading_id UUID NOT NULL REFERENCES readings(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT uk_reading_progress_user_reading UNIQUE (user_id, reading_id),
    CONSTRAINT ck_reading_progress_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_reading_progress_timestamps CHECK (
        (status = 'IN_PROGRESS' AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND completed_at IS NOT NULL)
    )
);

CREATE INDEX idx_reading_progress_user_status ON reading_progress(user_id, status);
CREATE INDEX idx_reading_progress_reading_id ON reading_progress(reading_id);

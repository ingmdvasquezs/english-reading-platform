-- V21: Schedule existing unscheduled KNOWN vocabulary entries deterministically across +7 to +30 days

WITH ranked_known AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY user_id
            ORDER BY first_seen_at ASC, id ASC
        ) AS row_num
    FROM user_vocabulary
    WHERE status = 'KNOWN'
      AND next_review_at IS NULL
)
UPDATE user_vocabulary uv
SET
    review_stage = GREATEST(3, uv.review_stage),
    next_review_at = (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')
        + INTERVAL '7 days'
        + (((rk.row_num - 1) % 24) * INTERVAL '1 day')
FROM ranked_known rk
WHERE uv.id = rk.id;

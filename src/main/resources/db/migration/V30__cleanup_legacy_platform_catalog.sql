-- V30: Clean legacy platform editorial catalog and legacy collections
-- Removes the 74 legacy platform readings, associated progress, word frequencies,
-- comprehension quiz questions/options/attempts/answers, collection memberships,
-- and the 6 legacy collections by explicit keys.
-- Preserves all user readings, documents, vocabulary, users, and infrastructure.

-- 1. Delete comprehension answers referencing platform attempts, questions, or options (defensive deletion)
DELETE FROM user_comprehension_answers
WHERE attempt_id IN (
    SELECT uca.id FROM user_comprehension_attempts uca
    JOIN readings r ON r.id = uca.reading_id
    WHERE r.origin = 'PLATFORM'
)
OR question_id IN (
    SELECT rcq.id FROM reading_comprehension_questions rcq
    JOIN readings r ON r.id = rcq.reading_id
    WHERE r.origin = 'PLATFORM'
)
OR selected_option_id IN (
    SELECT rco.id FROM reading_comprehension_options rco
    JOIN reading_comprehension_questions rcq ON rcq.id = rco.question_id
    JOIN readings r ON r.id = rcq.reading_id
    WHERE r.origin = 'PLATFORM'
);

-- 2. Delete comprehension attempts for platform readings
DELETE FROM user_comprehension_attempts
WHERE reading_id IN (
    SELECT id FROM readings WHERE origin = 'PLATFORM'
);

-- 3. Delete comprehension options for platform questions
DELETE FROM reading_comprehension_options
WHERE question_id IN (
    SELECT rcq.id FROM reading_comprehension_questions rcq
    JOIN readings r ON r.id = rcq.reading_id
    WHERE r.origin = 'PLATFORM'
);

-- 4. Delete comprehension questions for platform readings
DELETE FROM reading_comprehension_questions
WHERE reading_id IN (
    SELECT id FROM readings WHERE origin = 'PLATFORM'
);

-- 5. Delete collection memberships for platform readings (FK is NO ACTION)
DELETE FROM reading_collections
WHERE reading_id IN (
    SELECT id FROM readings WHERE origin = 'PLATFORM'
);

-- 6. Delete reading progress for platform readings
DELETE FROM reading_progress
WHERE reading_id IN (
    SELECT id FROM readings WHERE origin = 'PLATFORM'
);

-- 7. Delete word frequencies for platform readings
DELETE FROM reading_word_frequencies
WHERE reading_id IN (
    SELECT id FROM readings WHERE origin = 'PLATFORM'
);

-- 8. Delete legacy platform readings
DELETE FROM readings
WHERE origin = 'PLATFORM';

-- 9. Delete legacy collections by explicit keys
DELETE FROM collections
WHERE key IN (
    'everyday-life-human-connections',
    'mysteries-imagination',
    'science-technology-ideas',
    'nature-environment',
    'travel-places-memory',
    'culture-work-society'
);

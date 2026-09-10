ALTER TABLE readings
    ADD COLUMN origin VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD COLUMN editorial_level VARCHAR(2),
    ADD COLUMN category VARCHAR(100),
    ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE readings
    ADD CONSTRAINT ck_readings_origin
        CHECK (origin IN ('USER', 'PLATFORM')),
    ADD CONSTRAINT ck_readings_editorial_level
        CHECK (editorial_level IS NULL OR editorial_level IN ('A1', 'A2', 'B1', 'B2', 'C1')),
    ADD CONSTRAINT ck_readings_origin_ownership_metadata
        CHECK (
            (origin = 'USER' AND user_id IS NOT NULL
                AND editorial_level IS NULL AND category IS NULL)
            OR
            (origin = 'PLATFORM' AND user_id IS NULL
                AND editorial_level IS NOT NULL AND category IS NOT NULL
                AND length(trim(category)) > 0)
        );

CREATE INDEX idx_readings_platform_created_at_desc
    ON readings (created_at DESC, id)
    WHERE origin = 'PLATFORM';

INSERT INTO readings
    (id, user_id, title, content, language, created_at, origin, editorial_level, category)
VALUES
    ('10000000-0000-0000-0000-000000000001', NULL,
     'A Morning at the Library',
     'Mia visits the library every Saturday. She chooses a short book, sits near the window, and reads until lunchtime. The quiet room helps her concentrate.',
     'en', CURRENT_TIMESTAMP, 'PLATFORM', 'A1', 'Daily Life'),
    ('10000000-0000-0000-0000-000000000002', NULL,
     'Why Cities Need Trees',
     'Trees make crowded cities healthier and more comfortable. They provide shade, reduce heat, absorb rainwater, and offer shelter to birds. Urban planners increasingly treat trees as essential infrastructure.',
     'en', CURRENT_TIMESTAMP, 'PLATFORM', 'A2', 'Environment'),
    ('10000000-0000-0000-0000-000000000003', NULL,
     'The Changing Nature of Work',
     'Remote collaboration has changed how many organizations define the workplace. Flexibility can improve concentration and widen access to talent, but teams must communicate deliberately to preserve trust and shared purpose.',
     'en', CURRENT_TIMESTAMP, 'PLATFORM', 'B1', 'Work'),
    ('10000000-0000-0000-0000-000000000004', NULL,
     'When Algorithms Shape Attention',
     'Recommendation systems select information by predicting what will sustain attention. Although this can make digital services convenient, it may also narrow the range of ideas people encounter unless designers and readers actively seek variety.',
     'en', CURRENT_TIMESTAMP, 'PLATFORM', 'B2', 'Technology');

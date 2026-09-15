-- V31: Multilingual Open Foundation and Editorial Content Model
-- 1. Standardize language column capacities to VARCHAR(50)
ALTER TABLE readings ALTER COLUMN language TYPE VARCHAR(50);
ALTER TABLE words ALTER COLUMN language TYPE VARCHAR(50);
ALTER TABLE reading_word_frequencies ALTER COLUMN language TYPE VARCHAR(50);
ALTER TABLE users ALTER COLUMN learning_language TYPE VARCHAR(50);
ALTER TABLE users ALTER COLUMN native_language TYPE VARCHAR(50);

-- 2. Remove English-only check constraints on users and add open BCP-47 syntax validation
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_learning_language;
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_native_language;

ALTER TABLE users ADD CONSTRAINT ck_users_learning_language_format
    CHECK (learning_language ~ '^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$');
ALTER TABLE users ADD CONSTRAINT ck_users_native_language_format
    CHECK (native_language IS NULL OR native_language ~ '^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$');

-- 3. Open BCP-47 syntax checks on readings and words
ALTER TABLE readings ADD CONSTRAINT ck_readings_language_format
    CHECK (language ~ '^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$');
ALTER TABLE words ADD CONSTRAINT ck_words_language_format
    CHECK (language ~ '^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$');

-- 4. Add editorial metadata columns to readings
ALTER TABLE readings
    ADD COLUMN short_description VARCHAR(500),
    ADD COLUMN content_type VARCHAR(50),
    ADD COLUMN country_code VARCHAR(2),
    ADD COLUMN region VARCHAR(50),
    ADD COLUMN source_kind VARCHAR(50),
    ADD COLUMN rights_status VARCHAR(50),
    ADD COLUMN adaptation_kind VARCHAR(50),
    ADD COLUMN source_language VARCHAR(50),
    ADD COLUMN source_title VARCHAR(250),
    ADD COLUMN source_author VARCHAR(150),
    ADD COLUMN source_url VARCHAR(500),
    ADD COLUMN source_notes TEXT,
    ADD COLUMN adaptation_group_key VARCHAR(100),
    ADD COLUMN cover_attribution VARCHAR(250),
    ADD COLUMN access_tier VARCHAR(20);

-- 5. Update ownership and editorial metadata check constraint
ALTER TABLE readings DROP CONSTRAINT IF EXISTS ck_readings_origin_ownership_metadata;

ALTER TABLE readings
    ADD CONSTRAINT ck_readings_origin_ownership_metadata
        CHECK (
            (origin = 'USER' AND user_id IS NOT NULL
                AND editorial_level IS NULL AND category IS NULL AND editorial_status IS NULL
                AND short_description IS NULL AND content_type IS NULL AND country_code IS NULL
                AND region IS NULL AND source_kind IS NULL AND rights_status IS NULL
                AND adaptation_kind IS NULL AND source_language IS NULL AND source_title IS NULL
                AND source_author IS NULL AND source_url IS NULL AND source_notes IS NULL
                AND adaptation_group_key IS NULL AND cover_attribution IS NULL AND access_tier IS NULL)
            OR
            (origin = 'PLATFORM' AND user_id IS NULL
                AND editorial_level IS NOT NULL AND category IS NOT NULL AND length(trim(category)) > 0
                AND editorial_status IS NOT NULL
                AND short_description IS NOT NULL AND length(trim(short_description)) > 0
                AND content_type IS NOT NULL AND region IS NOT NULL
                AND source_kind IS NOT NULL AND rights_status IS NOT NULL AND adaptation_kind IS NOT NULL
                AND access_tier IS NOT NULL)
        );

-- 6. Constrain editorial taxonomy values
ALTER TABLE readings
    ADD CONSTRAINT ck_readings_category_canonical
        CHECK (category IS NULL OR category IN (
            'Daily Life & Relationships',
            'Work & Society',
            'Science & Technology',
            'Nature & Environment',
            'Mystery & Exploration',
            'Travel & Places',
            'Culture, Arts & Fiction',
            'History & Memory'
        )),
    ADD CONSTRAINT ck_readings_content_type
        CHECK (content_type IS NULL OR content_type IN (
            'LEGEND', 'MYTH', 'HISTORICAL_ACCOUNT', 'BIOGRAPHY', 'REAL_STORY',
            'FICTION', 'EXPLAINER', 'TRAVEL_NARRATIVE', 'DIALOGUE', 'ANECDOTE'
        )),
    ADD CONSTRAINT ck_readings_region
        CHECK (region IS NULL OR region IN (
            'SOUTH_AMERICA', 'CENTRAL_AMERICA', 'CARIBBEAN', 'NORTHERN_AMERICA', 'EUROPE',
            'EAST_ASIA', 'SOUTHEAST_ASIA', 'SOUTH_ASIA', 'CENTRAL_ASIA', 'MIDDLE_EAST',
            'NORTH_AFRICA', 'SUB_SAHARAN_AFRICA', 'OCEANIA', 'GLOBAL'
        )),
    ADD CONSTRAINT ck_readings_source_kind
        CHECK (source_kind IS NULL OR source_kind IN (
            'ORAL_TRADITION', 'HISTORICAL_SOURCE', 'LITERARY_WORK', 'FACTUAL_REFERENCE', 'ORIGINAL_EDITORIAL'
        )),
    ADD CONSTRAINT ck_readings_rights_status
        CHECK (rights_status IS NULL OR rights_status IN (
            'PUBLIC_DOMAIN', 'ORIGINAL', 'LICENSED', 'UNKNOWN'
        )),
    ADD CONSTRAINT ck_readings_adaptation_kind
        CHECK (adaptation_kind IS NULL OR adaptation_kind IN (
            'ORIGINAL', 'PEDAGOGICAL_ADAPTATION', 'TRANSLATED_ADAPTATION', 'CURATED_EXCERPT'
        )),
    ADD CONSTRAINT ck_readings_access_tier
        CHECK (access_tier IS NULL OR access_tier IN ('FREE', 'PREMIUM')),
    ADD CONSTRAINT ck_readings_country_code
        CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$'),
    ADD CONSTRAINT ck_readings_source_language_format
        CHECK (source_language IS NULL OR source_language ~ '^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$'),
    ADD CONSTRAINT ck_readings_translated_adaptation_source_language
        CHECK (adaptation_kind != 'TRANSLATED_ADAPTATION' OR source_language IS NOT NULL);

-- 7. Multilingual adaptation partial unique index
CREATE UNIQUE INDEX uq_readings_platform_adaptation_lang_level
    ON readings (adaptation_group_key, language, editorial_level)
    WHERE adaptation_group_key IS NOT NULL AND origin = 'PLATFORM';

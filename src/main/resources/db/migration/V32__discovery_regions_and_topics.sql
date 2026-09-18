-- V32: Discovery Regions, Countries, Hero Images and Reading Discovery Topics

-- 1. Discovery Regions table
CREATE TABLE discovery_regions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    region_key VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    subtitle VARCHAR(255),
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 2. Discovery Countries table
CREATE TABLE discovery_countries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    region_id UUID NOT NULL REFERENCES discovery_regions(id) ON DELETE CASCADE,
    country_code VARCHAR(2) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    tagline VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_discovery_countries_code CHECK (country_code ~ '^[A-Z]{2}$')
);

-- 3. Discovery Country Hero Images table
CREATE TABLE discovery_country_hero_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    country_id UUID NOT NULL REFERENCES discovery_countries(id) ON DELETE CASCADE,
    asset_key VARCHAR(255) NOT NULL,
    location VARCHAR(255),
    alt VARCHAR(255),
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 4. Add discovery_topic to readings
ALTER TABLE readings
    ADD COLUMN discovery_topic VARCHAR(50);

-- 5. Deterministic backfill for the 15 current published platform readings of Colombia
UPDATE readings
SET discovery_topic = 'MYTHS_AND_LEGENDS'
WHERE origin = 'PLATFORM' AND country_code = 'CO';

-- 6. Add check constraint for canonical discovery topics
ALTER TABLE readings
    ADD CONSTRAINT ck_readings_discovery_topic
        CHECK (discovery_topic IS NULL OR discovery_topic IN (
            'MYTHS_AND_LEGENDS',
            'REAL_STORIES',
            'HISTORY_AND_MEMORY',
            'CULTURE_AND_TRADITIONS',
            'NATURE_AND_PLACES',
            'PEOPLE'
        ));

-- 7. Update origin ownership check constraint to ensure user readings do not have discovery_topic
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
                AND adaptation_group_key IS NULL AND cover_attribution IS NULL AND access_tier IS NULL
                AND discovery_topic IS NULL)
            OR
            (origin = 'PLATFORM' AND user_id IS NULL
                AND editorial_level IS NOT NULL AND category IS NOT NULL AND length(trim(category)) > 0
                AND editorial_status IS NOT NULL
                AND short_description IS NOT NULL AND length(trim(short_description)) > 0
                AND content_type IS NOT NULL AND region IS NOT NULL
                AND source_kind IS NOT NULL AND rights_status IS NOT NULL AND adaptation_kind IS NOT NULL
                AND access_tier IS NOT NULL)
        );

-- 8. Seed initial Discovery Region: latin-america
INSERT INTO discovery_regions (id, region_key, display_name, subtitle, display_order, active)
VALUES (
    'a1000000-0000-0000-0000-000000000001',
    'latin-america',
    'Latinoamérica',
    'Historias, cultura y lugares de nuestra región.',
    1,
    TRUE
);

-- 9. Seed initial Discovery Country: CO (Colombia)
INSERT INTO discovery_countries (id, region_id, country_code, display_name, tagline, description, display_order, active)
VALUES (
    'b1000000-0000-0000-0000-000000000001',
    'a1000000-0000-0000-0000-000000000001',
    'CO',
    'Colombia',
    'Historias, lugares, mitos y tradiciones para aprender inglés leyendo.',
    'Personas extraordinarias. Lugares inolvidables. Historias que trascienden el tiempo.',
    1,
    TRUE
);

-- 10. Seed initial Hero Images for Colombia
INSERT INTO discovery_country_hero_images (id, country_id, asset_key, location, alt, display_order)
VALUES
    (
        'c1000000-0000-0000-0000-000000000001',
        'b1000000-0000-0000-0000-000000000001',
        'editorial/heroes/colombia/hero-colombia-villa-de-leyva.webp',
        'Villa de Leyva, Boyacá',
        'Villa de Leyva, Boyacá',
        1
    ),
    (
        'c1000000-0000-0000-0000-000000000002',
        'b1000000-0000-0000-0000-000000000001',
        'editorial/heroes/colombia/hero-colombia-valle-de-cocora.webp',
        'Valle de Cocora, Quindío',
        'Valle de Cocora, Quindío',
        2
    );

-- =============================================================================
-- V41: Persist Canonical Mexico Country Hero Asset Key
-- Replaces legacy editorial hero images for Mexico with the canonical platform
-- asset: countries/MX/hero.webp
-- =============================================================================

DELETE FROM discovery_country_hero_images
WHERE country_id IN (SELECT id FROM discovery_countries WHERE country_code = 'MX');

INSERT INTO discovery_country_hero_images (id, country_id, asset_key, location, alt, display_order)
SELECT
    'c1000000-0000-0000-0000-000000000004',
    id,
    'countries/MX/hero.webp',
    'México',
    'México',
    1
FROM discovery_countries
WHERE country_code = 'MX';

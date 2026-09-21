-- V35: Seed Discovery Country MX (Mexico) and Hero Images

INSERT INTO discovery_countries (id, region_id, country_code, display_name, tagline, description, display_order, active)
VALUES (
    'b1000000-0000-0000-0000-000000000002',
    'a1000000-0000-0000-0000-000000000001',
    'MX',
    'México',
    'Historias, naturaleza, tradiciones y cultura para aprender inglés leyendo.',
    'Paisajes volcánicos, civilizaciones ancestrales y tradiciones vivas que conectan el pasado con el presente.',
    2,
    TRUE
)
ON CONFLICT (country_code) DO NOTHING;

INSERT INTO discovery_country_hero_images (id, country_id, asset_key, location, alt, display_order)
VALUES
    (
        'c1000000-0000-0000-0000-000000000003',
        'b1000000-0000-0000-0000-000000000002',
        'editorial/heroes/mexico/hero-mexico-popocatepetl.webp',
        'Paso de Cortés, Popocatépetl e Iztaccíhuatl',
        'Paso de Cortés, Popocatépetl e Iztaccíhuatl',
        1
    ),
    (
        'c1000000-0000-0000-0000-000000000004',
        'b1000000-0000-0000-0000-000000000002',
        'editorial/heroes/mexico/hero-mexico-chichen-itza.webp',
        'El Castillo, Chichén Itzá, Yucatán',
        'El Castillo, Chichén Itzá, Yucatán',
        2
    )
ON CONFLICT (id) DO NOTHING;

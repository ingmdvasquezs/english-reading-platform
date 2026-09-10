CREATE TABLE collections (
    id UUID PRIMARY KEY,
    key VARCHAR(100) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    description VARCHAR(500) NOT NULL,
    display_order INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    cover_key VARCHAR(120),
    CONSTRAINT uk_collections_key UNIQUE (key),
    CONSTRAINT ck_collections_display_order_positive CHECK (display_order > 0)
);

CREATE TABLE reading_collections (
    collection_id UUID NOT NULL,
    reading_id UUID NOT NULL,
    display_order INTEGER NOT NULL,
    CONSTRAINT pk_reading_collections PRIMARY KEY (collection_id, reading_id),
    CONSTRAINT fk_reading_collections_collection FOREIGN KEY (collection_id)
        REFERENCES collections (id),
    CONSTRAINT fk_reading_collections_reading FOREIGN KEY (reading_id)
        REFERENCES readings (id),
    CONSTRAINT ck_reading_collections_display_order_positive CHECK (display_order > 0)
);

CREATE INDEX idx_reading_collections_collection_order
    ON reading_collections (collection_id, display_order, reading_id);

INSERT INTO collections
    (id, key, display_name, description, display_order, active, cover_key)
VALUES
    ('40000000-0000-0000-0000-000000000001', 'everyday-life-human-connections',
     'Everyday Life & Human Connections',
     'Warm stories about daily routines, relationships, cooperation, and belonging.', 1, TRUE, NULL),
    ('40000000-0000-0000-0000-000000000002', 'mysteries-imagination',
     'Mysteries & Imagination',
     'Curious mysteries and imaginative worlds that reward attention and wonder.', 2, TRUE, NULL),
    ('40000000-0000-0000-0000-000000000003', 'science-technology-ideas',
     'Science, Technology & Ideas',
     'Readings about discovery, technology, evidence, and ideas that shape our lives.', 3, TRUE, NULL),
    ('40000000-0000-0000-0000-000000000004', 'nature-environment',
     'Nature & Environment',
     'Journeys through wildlife, landscapes, climate, and caring for the natural world.', 4, TRUE, NULL),
    ('40000000-0000-0000-0000-000000000005', 'travel-places-memory',
     'Travel, Places & Memory',
     'Stories of journeys, meaningful places, maps, and the memories they hold.', 5, TRUE, NULL),
    ('40000000-0000-0000-0000-000000000006', 'culture-work-society',
     'Culture, Work & Society',
     'Perspectives on culture, workplaces, communities, and how societies change.', 6, TRUE, NULL);

-- The 115 array values reproduce the EH/MI/ST/NE/TP/CS assignments from the editorial audit.
-- Editorial order initially follows the stable catalog UUID order within each collection.
WITH audited_assignments (reading_id, collection_keys) AS (
    VALUES
    ('10000000-0000-0000-0000-000000000001'::UUID, ARRAY['everyday-life-human-connections']),
    ('10000000-0000-0000-0000-000000000002'::UUID, ARRAY['nature-environment']),
    ('10000000-0000-0000-0000-000000000003'::UUID, ARRAY['culture-work-society']),
    ('10000000-0000-0000-0000-000000000004'::UUID, ARRAY['science-technology-ideas']),
    ('20000000-0000-0000-0000-000000000001'::UUID, ARRAY['everyday-life-human-connections']),
    ('20000000-0000-0000-0000-000000000002'::UUID, ARRAY['nature-environment','everyday-life-human-connections']),
    ('20000000-0000-0000-0000-000000000003'::UUID, ARRAY['mysteries-imagination','nature-environment']),
    ('20000000-0000-0000-0000-000000000004'::UUID, ARRAY['everyday-life-human-connections']),
    ('20000000-0000-0000-0000-000000000005'::UUID, ARRAY['travel-places-memory']),
    ('20000000-0000-0000-0000-000000000006'::UUID, ARRAY['nature-environment']),
    ('20000000-0000-0000-0000-000000000007'::UUID, ARRAY['mysteries-imagination']),
    ('20000000-0000-0000-0000-000000000008'::UUID, ARRAY['culture-work-society']),
    ('20000000-0000-0000-0000-000000000009'::UUID, ARRAY['everyday-life-human-connections','science-technology-ideas']),
    ('20000000-0000-0000-0000-000000000010'::UUID, ARRAY['culture-work-society','nature-environment']),
    ('20000000-0000-0000-0000-000000000011'::UUID, ARRAY['science-technology-ideas']),
    ('20000000-0000-0000-0000-000000000012'::UUID, ARRAY['travel-places-memory']),
    ('20000000-0000-0000-0000-000000000013'::UUID, ARRAY['science-technology-ideas']),
    ('20000000-0000-0000-0000-000000000014'::UUID, ARRAY['culture-work-society']),
    ('20000000-0000-0000-0000-000000000015'::UUID, ARRAY['science-technology-ideas','nature-environment']),
    ('20000000-0000-0000-0000-000000000016'::UUID, ARRAY['mysteries-imagination','travel-places-memory']),
    ('20000000-0000-0000-0000-000000000017'::UUID, ARRAY['culture-work-society','science-technology-ideas']),
    ('20000000-0000-0000-0000-000000000018'::UUID, ARRAY['science-technology-ideas','culture-work-society']),
    ('20000000-0000-0000-0000-000000000019'::UUID, ARRAY['culture-work-society','travel-places-memory']),
    ('20000000-0000-0000-0000-000000000020'::UUID, ARRAY['travel-places-memory']),
    ('30000000-0000-0000-0000-000000000001'::UUID, ARRAY['everyday-life-human-connections']),
    ('30000000-0000-0000-0000-000000000002'::UUID, ARRAY['everyday-life-human-connections','culture-work-society']),
    ('30000000-0000-0000-0000-000000000003'::UUID, ARRAY['mysteries-imagination']),
    ('30000000-0000-0000-0000-000000000004'::UUID, ARRAY['everyday-life-human-connections']),
    ('30000000-0000-0000-0000-000000000005'::UUID, ARRAY['travel-places-memory']),
    ('30000000-0000-0000-0000-000000000006'::UUID, ARRAY['mysteries-imagination']),
    ('30000000-0000-0000-0000-000000000007'::UUID, ARRAY['culture-work-society']),
    ('30000000-0000-0000-0000-000000000008'::UUID, ARRAY['culture-work-society']),
    ('30000000-0000-0000-0000-000000000009'::UUID, ARRAY['mysteries-imagination','travel-places-memory']),
    ('30000000-0000-0000-0000-000000000010'::UUID, ARRAY['culture-work-society','science-technology-ideas']),
    ('30000000-0000-0000-0000-000000000011'::UUID, ARRAY['culture-work-society','nature-environment']),
    ('30000000-0000-0000-0000-000000000012'::UUID, ARRAY['travel-places-memory']),
    ('30000000-0000-0000-0000-000000000013'::UUID, ARRAY['science-technology-ideas','mysteries-imagination']),
    ('30000000-0000-0000-0000-000000000014'::UUID, ARRAY['culture-work-society','everyday-life-human-connections']),
    ('30000000-0000-0000-0000-000000000015'::UUID, ARRAY['travel-places-memory']),
    ('30000000-0000-0000-0000-000000000016'::UUID, ARRAY['culture-work-society','everyday-life-human-connections']),
    ('30000000-0000-0000-0000-000000000017'::UUID, ARRAY['mysteries-imagination','travel-places-memory']),
    ('30000000-0000-0000-0000-000000000018'::UUID, ARRAY['science-technology-ideas','nature-environment']),
    ('30000000-0000-0000-0000-000000000019'::UUID, ARRAY['culture-work-society','nature-environment']),
    ('30000000-0000-0000-0000-000000000020'::UUID, ARRAY['culture-work-society']),
    ('30000000-0000-0000-0000-000000000021'::UUID, ARRAY['travel-places-memory']),
    ('30000000-0000-0000-0000-000000000022'::UUID, ARRAY['mysteries-imagination','culture-work-society']),
    ('30000000-0000-0000-0000-000000000023'::UUID, ARRAY['culture-work-society']),
    ('30000000-0000-0000-0000-000000000024'::UUID, ARRAY['science-technology-ideas','nature-environment']),
    ('30000000-0000-0000-0000-000000000025'::UUID, ARRAY['everyday-life-human-connections','culture-work-society']),
    ('30000000-0000-0000-0000-000000000026'::UUID, ARRAY['science-technology-ideas']),
    ('30000000-0000-0000-0000-000000000027'::UUID, ARRAY['mysteries-imagination','travel-places-memory']),
    ('30000000-0000-0000-0000-000000000028'::UUID, ARRAY['culture-work-society']),
    ('30000000-0000-0000-0000-000000000029'::UUID, ARRAY['nature-environment','science-technology-ideas']),
    ('30000000-0000-0000-0000-000000000030'::UUID, ARRAY['mysteries-imagination','culture-work-society']),
    ('30000000-0000-0000-0000-000000000031'::UUID, ARRAY['culture-work-society']),
    ('30000000-0000-0000-0000-000000000032'::UUID, ARRAY['mysteries-imagination','nature-environment','travel-places-memory']),
    ('30000000-0000-0000-0000-000000000033'::UUID, ARRAY['culture-work-society']),
    ('30000000-0000-0000-0000-000000000034'::UUID, ARRAY['culture-work-society','travel-places-memory']),
    ('30000000-0000-0000-0000-000000000035'::UUID, ARRAY['travel-places-memory','nature-environment']),
    ('30000000-0000-0000-0000-000000000036'::UUID, ARRAY['science-technology-ideas','nature-environment']),
    ('30000000-0000-0000-0000-000000000037'::UUID, ARRAY['culture-work-society']),
    ('30000000-0000-0000-0000-000000000038'::UUID, ARRAY['culture-work-society','travel-places-memory']),
    ('30000000-0000-0000-0000-000000000039'::UUID, ARRAY['science-technology-ideas','culture-work-society']),
    ('30000000-0000-0000-0000-000000000040'::UUID, ARRAY['mysteries-imagination','science-technology-ideas']),
    ('30000000-0000-0000-0000-000000000041'::UUID, ARRAY['nature-environment','travel-places-memory']),
    ('30000000-0000-0000-0000-000000000042'::UUID, ARRAY['culture-work-society']),
    ('30000000-0000-0000-0000-000000000043'::UUID, ARRAY['culture-work-society','science-technology-ideas']),
    ('30000000-0000-0000-0000-000000000044'::UUID, ARRAY['science-technology-ideas','nature-environment']),
    ('30000000-0000-0000-0000-000000000045'::UUID, ARRAY['travel-places-memory','nature-environment']),
    ('30000000-0000-0000-0000-000000000046'::UUID, ARRAY['science-technology-ideas','culture-work-society']),
    ('30000000-0000-0000-0000-000000000047'::UUID, ARRAY['culture-work-society','travel-places-memory']),
    ('30000000-0000-0000-0000-000000000048'::UUID, ARRAY['nature-environment','culture-work-society']),
    ('30000000-0000-0000-0000-000000000049'::UUID, ARRAY['culture-work-society']),
    ('30000000-0000-0000-0000-000000000050'::UUID, ARRAY['mysteries-imagination','science-technology-ideas'])
), expanded AS (
    SELECT reading_id, unnest(collection_keys) AS collection_key
    FROM audited_assignments
), ordered AS (
    SELECT reading_id, collection_key,
           row_number() OVER (PARTITION BY collection_key ORDER BY reading_id) AS display_order
    FROM expanded
)
INSERT INTO reading_collections (collection_id, reading_id, display_order)
SELECT collection.id, ordered.reading_id, ordered.display_order
FROM ordered
JOIN collections collection ON collection.key = ordered.collection_key;

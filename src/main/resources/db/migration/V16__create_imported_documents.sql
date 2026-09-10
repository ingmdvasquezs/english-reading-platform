CREATE TABLE imported_documents (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL,
    author VARCHAR(300),
    language VARCHAR(50) NOT NULL,
    format VARCHAR(20) NOT NULL,
    import_status VARCHAR(20) NOT NULL,
    cover_asset_key VARCHAR(500),
    source_asset_key VARCHAR(500),
    original_filename VARCHAR(500),
    source_sha256 CHAR(64) NOT NULL,
    chunking_version INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_imported_documents_id_user UNIQUE (id, user_id),
    CONSTRAINT ck_imported_documents_language CHECK (
        language ~ '^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$'
    ),
    CONSTRAINT ck_imported_documents_format CHECK (format IN ('EPUB', 'PDF')),
    CONSTRAINT ck_imported_documents_status CHECK (
        import_status IN ('PROCESSING', 'READY', 'FAILED')
    ),
    CONSTRAINT ck_imported_documents_sha256 CHECK (source_sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT ck_imported_documents_chunking_version CHECK (chunking_version > 0),
    CONSTRAINT ck_imported_documents_timestamps CHECK (updated_at >= created_at)
);

CREATE INDEX idx_imported_documents_user_created
    ON imported_documents (user_id, created_at DESC);

CREATE TABLE document_sections (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES imported_documents(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL,
    title VARCHAR(500),
    source_locator VARCHAR(1000),
    CONSTRAINT uk_document_sections_document_ordinal UNIQUE (document_id, ordinal),
    CONSTRAINT uk_document_sections_id_document UNIQUE (id, document_id),
    CONSTRAINT ck_document_sections_ordinal CHECK (ordinal > 0)
);

CREATE TABLE document_units (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES imported_documents(id) ON DELETE CASCADE,
    section_id UUID,
    global_ordinal INTEGER NOT NULL,
    section_ordinal INTEGER NOT NULL,
    unit_kind VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    word_count INTEGER NOT NULL,
    source_locator VARCHAR(1000),
    content_hash CHAR(64),
    CONSTRAINT fk_document_units_section_document FOREIGN KEY (section_id, document_id)
        REFERENCES document_sections (id, document_id) ON DELETE CASCADE,
    CONSTRAINT uk_document_units_document_global UNIQUE (document_id, global_ordinal),
    CONSTRAINT uk_document_units_section_ordinal UNIQUE (section_id, section_ordinal),
    CONSTRAINT uk_document_units_id_document UNIQUE (id, document_id),
    CONSTRAINT ck_document_units_global_ordinal CHECK (global_ordinal > 0),
    CONSTRAINT ck_document_units_section_ordinal CHECK (section_ordinal > 0),
    CONSTRAINT ck_document_units_kind CHECK (unit_kind IN ('LOGICAL_CHUNK', 'PHYSICAL_PAGE')),
    CONSTRAINT ck_document_units_content CHECK (length(btrim(content)) > 0),
    CONSTRAINT ck_document_units_word_count CHECK (word_count >= 0),
    CONSTRAINT ck_document_units_content_hash CHECK (
        content_hash IS NULL OR content_hash ~ '^[a-f0-9]{64}$'
    )
);

CREATE TABLE document_progress (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    document_id UUID NOT NULL,
    current_unit_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    last_read_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_document_progress_owned_document FOREIGN KEY (document_id, user_id)
        REFERENCES imported_documents (id, user_id) ON DELETE CASCADE,
    CONSTRAINT fk_document_progress_current_unit FOREIGN KEY (current_unit_id, document_id)
        REFERENCES document_units (id, document_id),
    CONSTRAINT uk_document_progress_user_document UNIQUE (user_id, document_id),
    CONSTRAINT ck_document_progress_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_document_progress_timestamps CHECK (
        last_read_at >= started_at
        AND (
            (status = 'IN_PROGRESS' AND completed_at IS NULL)
            OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND completed_at >= started_at)
        )
    )
);

CREATE INDEX idx_document_progress_user_status_activity
    ON document_progress (user_id, status, last_read_at DESC);

-- PostgreSQL minimal schema + tags

CREATE TYPE execution_mode AS ENUM ('TEST', 'RUN');

CREATE TABLE artifact_store
(
    hash_hex   CHAR(64) PRIMARY KEY,
    size_bytes BIGINT      NOT NULL,
    content    BYTEA       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE operation_chain_config
(
    al_id                              VARCHAR(200) PRIMARY KEY,
    allow_run_publication_without_test BOOLEAN     NOT NULL DEFAULT FALSE,
    store_type                         VARCHAR(30) NOT NULL,
    store_props                        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at                         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE operation_chain_object
(
    id           BIGSERIAL PRIMARY KEY,
    al_id        VARCHAR(200)   NOT NULL,
    version      VARCHAR(100)   NOT NULL,
    mode         execution_mode NOT NULL,
    content_hash CHAR(64)       NOT NULL,
    size_bytes   BIGINT         NOT NULL,
    mime_type    VARCHAR(100)   NOT NULL DEFAULT 'application/xml',
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(200) NULL,
    published_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE (al_id, version, mode)
);

CREATE TABLE operation_chain_tag
(
    al_id VARCHAR(200) NOT NULL,
    tag   VARCHAR(100) NOT NULL,
    PRIMARY KEY (al_id, tag),
    FOREIGN KEY (al_id) REFERENCES operation_chain_config (al_id) ON DELETE CASCADE
);

CREATE TABLE operation_chain_object_tag
(
    object_id BIGINT       NOT NULL,
    tag       VARCHAR(100) NOT NULL,
    PRIMARY KEY (object_id, tag),
    FOREIGN KEY (object_id) REFERENCES operation_chain_object (id) ON DELETE CASCADE
);

CREATE INDEX idx_op_chain_latest_run ON operation_chain_object (al_id, published_at DESC) WHERE mode = 'RUN';
CREATE INDEX idx_op_chain_by_hash ON operation_chain_object (content_hash);
CREATE INDEX idx_tag_value ON operation_chain_tag (tag);
CREATE INDEX idx_obj_tag_value ON operation_chain_object_tag (tag);

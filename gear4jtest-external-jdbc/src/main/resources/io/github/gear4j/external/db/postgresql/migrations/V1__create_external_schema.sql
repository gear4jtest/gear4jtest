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
    al_id                              VARCHAR(255) PRIMARY KEY,
    allow_run_publication_without_test BOOLEAN     NOT NULL DEFAULT FALSE,
    store_type                         VARCHAR(30) NOT NULL,
    store_props                        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at                         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE operation_chain_object
(
    id           BIGSERIAL PRIMARY KEY,
    al_id        VARCHAR(255)   NOT NULL,
    version      VARCHAR(100)   NOT NULL,
    publication_mode execution_mode NOT NULL,
    content_hash CHAR(64)       NOT NULL,
    size_bytes   BIGINT         NOT NULL,
    mime_type    VARCHAR(100)   NOT NULL DEFAULT 'application/xml',
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(200) NULL,
    published_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    UNIQUE (al_id, version, publication_mode)
);


CREATE TABLE operation_chain_publication_stage
(
    stage_id         VARCHAR(36) PRIMARY KEY,
    al_id            VARCHAR(255) NOT NULL,
    version          VARCHAR(100) NOT NULL,
    publication_mode execution_mode NOT NULL,
    content_hash     CHAR(64) NOT NULL,
    size_bytes       BIGINT NOT NULL,
    mime_type        VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    created_by       VARCHAR(200),
    published_at     TIMESTAMPTZ NOT NULL,
    store_fingerprint CHAR(64) NOT NULL,
    staged_at        TIMESTAMPTZ NOT NULL,
    stage_revision   BIGINT NOT NULL DEFAULT 1,
    UNIQUE (al_id, version, publication_mode)
);

CREATE TABLE operation_chain_publication_stage_tag
(
    stage_id VARCHAR(36) NOT NULL,
    tag      VARCHAR(100) NOT NULL,
    PRIMARY KEY (stage_id, tag),
    FOREIGN KEY (stage_id) REFERENCES operation_chain_publication_stage (stage_id) ON DELETE CASCADE
);

CREATE INDEX idx_op_chain_stage_age
    ON operation_chain_publication_stage (staged_at, stage_id);

CREATE TABLE operation_chain_tag
(
    al_id VARCHAR(255) NOT NULL,
    tag   VARCHAR(100) NOT NULL,
    PRIMARY KEY (al_id, tag),
    FOREIGN KEY (al_id) REFERENCES operation_chain_config (al_id) ON DELETE CASCADE
);

CREATE INDEX idx_op_chain_latest_run
    ON operation_chain_object (al_id, published_at DESC, id DESC)
    WHERE publication_mode = 'RUN';
CREATE INDEX idx_op_chain_all
    ON operation_chain_object (al_id, published_at DESC, id DESC);
CREATE INDEX idx_op_chain_by_hash ON operation_chain_object (content_hash);
CREATE INDEX idx_tag_value ON operation_chain_tag (tag);

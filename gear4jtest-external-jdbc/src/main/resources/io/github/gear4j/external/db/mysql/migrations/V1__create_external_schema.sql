-- MySQL 8 minimal schema + tags

CREATE TABLE artifact_store
(
    hash_hex   CHAR(64) PRIMARY KEY,
    size_bytes BIGINT    NOT NULL,
    content    LONGBLOB  NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE operation_chain_config
(
    al_id                              VARCHAR(255) PRIMARY KEY,
    allow_run_publication_without_test TINYINT(1) NOT NULL DEFAULT 0,
    store_type                         ENUM('DATABASE','FILESYSTEM','S3','SFTP','MEMORY') NOT NULL,
    store_props                        JSON      NOT NULL,
    created_at                         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE operation_chain_object
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    al_id        VARCHAR(255) NOT NULL,
    version      VARCHAR(100) NOT NULL,
    publication_mode ENUM('TEST','RUN') NOT NULL,
    content_hash CHAR(64)     NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    mime_type    VARCHAR(100) NOT NULL DEFAULT 'application/xml',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(200) NULL,
    published_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_op_chain (al_id, version, publication_mode),
    KEY          idx_op_chain_by_hash (content_hash),
    KEY          idx_op_chain_latest_run (al_id, publication_mode, published_at DESC, id DESC),
    KEY          idx_op_chain_all (al_id, published_at DESC, id DESC)
);


CREATE TABLE operation_chain_publication_stage
(
    stage_id         VARCHAR(36) PRIMARY KEY,
    al_id            VARCHAR(255) NOT NULL,
    version          VARCHAR(100) NOT NULL,
    publication_mode ENUM('TEST','RUN') NOT NULL,
    content_hash     CHAR(64) NOT NULL,
    size_bytes       BIGINT NOT NULL,
    mime_type        VARCHAR(100) NOT NULL,
    created_at       TIMESTAMP NOT NULL,
    created_by       VARCHAR(200) NULL,
    published_at     TIMESTAMP NOT NULL,
    store_fingerprint CHAR(64) NOT NULL,
    staged_at        TIMESTAMP NOT NULL,
    stage_revision   BIGINT NOT NULL DEFAULT 1,
    UNIQUE KEY uq_op_chain_stage (al_id, version, publication_mode),
    KEY idx_op_chain_stage_age (staged_at, stage_id)
);

CREATE TABLE operation_chain_publication_stage_tag
(
    stage_id VARCHAR(36) NOT NULL,
    tag      VARCHAR(100) NOT NULL,
    PRIMARY KEY (stage_id, tag),
    CONSTRAINT fk_op_chain_stage_tag FOREIGN KEY (stage_id)
        REFERENCES operation_chain_publication_stage (stage_id) ON DELETE CASCADE
);

CREATE TABLE operation_chain_tag
(
    al_id VARCHAR(255) NOT NULL,
    tag   VARCHAR(100) NOT NULL,
    PRIMARY KEY (al_id, tag),
    CONSTRAINT fk_operation_chain_tag_config FOREIGN KEY (al_id)
        REFERENCES operation_chain_config (al_id) ON DELETE CASCADE
);

CREATE INDEX idx_tag_value ON operation_chain_tag (tag);

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
    al_id                              VARCHAR(200) PRIMARY KEY,
    allow_run_publication_without_test TINYINT(1) NOT NULL DEFAULT 0,
    store_type                         ENUM('DATABASE','FILESYSTEM','S3','SFTP','MEMORY') NOT NULL,
    store_props                        JSON      NOT NULL,
    created_at                         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE operation_chain_object
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    al_id        VARCHAR(200) NOT NULL,
    version      VARCHAR(100) NOT NULL,
    mode         ENUM('TEST','RUN') NOT NULL,
    content_hash CHAR(64)     NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    mime_type    VARCHAR(100) NOT NULL DEFAULT 'application/xml',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(200) NULL,
    published_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_op_chain (al_id, version, mode),
    KEY          idx_op_chain_by_hash (content_hash),
    KEY          idx_op_chain_latest_run (al_id, published_at)
);

CREATE TABLE operation_chain_tag
(
    al_id VARCHAR(200) NOT NULL,
    tag   VARCHAR(100) NOT NULL,
    PRIMARY KEY (al_id, tag)
);

CREATE INDEX idx_tag_value ON operation_chain_tag (tag);

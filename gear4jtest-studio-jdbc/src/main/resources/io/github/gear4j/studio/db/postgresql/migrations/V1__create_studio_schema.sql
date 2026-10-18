CREATE TABLE g4js_context (
    singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
    context_id VARCHAR(255)
);
INSERT INTO g4js_context(singleton,context_id) VALUES(1,NULL);
CREATE TABLE g4js_resource (
    id VARCHAR(36) PRIMARY KEY,
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('DOMAIN','CHAIN','DRAFT')),
    owner_subject VARCHAR(255) NOT NULL,
    inherit_access BOOLEAN NOT NULL,
    entity_version BIGINT NOT NULL CHECK (entity_version > 0),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);
CREATE TABLE g4js_domain (
    resource_id VARCHAR(36) PRIMARY KEY REFERENCES g4js_resource(id),
    resource_key VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    description VARCHAR(2048) NOT NULL,
    archived BOOLEAN NOT NULL
);
CREATE TABLE g4js_chain (
    resource_id VARCHAR(36) PRIMARY KEY REFERENCES g4js_resource(id),
    domain_id VARCHAR(36) NOT NULL REFERENCES g4js_domain(resource_id),
    runtime_assembly_line_id VARCHAR(64) NOT NULL UNIQUE,
    resource_key VARCHAR(64) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    description VARCHAR(2048) NOT NULL,
    archived BOOLEAN NOT NULL,
    UNIQUE(domain_id,resource_key)
);
CREATE INDEX idx_g4js_chain_domain ON g4js_chain(domain_id,resource_id);
CREATE TABLE g4js_draft (
    resource_id VARCHAR(36) PRIMARY KEY REFERENCES g4js_resource(id),
    chain_id VARCHAR(36) NOT NULL REFERENCES g4js_chain(resource_id),
    display_name VARCHAR(160) NOT NULL,
    abandoned BOOLEAN NOT NULL,
    head_number BIGINT NOT NULL CHECK (head_number > 0),
    origin VARCHAR(16) NOT NULL CHECK (origin IN ('STUDIO','XML_IMPORT')),
    base_revision_id VARCHAR(36)
);
CREATE INDEX idx_g4js_draft_chain ON g4js_draft(chain_id,resource_id);
CREATE TABLE g4js_revision (
    id VARCHAR(36) PRIMARY KEY,
    draft_id VARCHAR(36) NOT NULL REFERENCES g4js_draft(resource_id),
    revision_number BIGINT NOT NULL CHECK (revision_number > 0),
    author VARCHAR(255) NOT NULL,
    saved_at BIGINT NOT NULL,
    origin VARCHAR(16) NOT NULL CHECK (origin IN ('STUDIO','XML_IMPORT')),
    revision_kind VARCHAR(16) NOT NULL,
    definition_json TEXT,
    source_text TEXT,
    source_media_type VARCHAR(128),
    definition_hash VARCHAR(64) NOT NULL,
    CHECK (CASE revision_kind
        WHEN 'STRUCTURED' THEN definition_json IS NOT NULL AND source_text IS NULL AND source_media_type IS NULL
        WHEN 'SOURCE' THEN definition_json IS NULL AND source_text IS NOT NULL AND source_media_type IS NOT NULL
        ELSE FALSE END),
    UNIQUE(draft_id,revision_number)
);
ALTER TABLE g4js_draft ADD CONSTRAINT fk_g4js_draft_base_revision FOREIGN KEY(base_revision_id) REFERENCES g4js_revision(id);
CREATE TABLE g4js_acl (
    resource_id VARCHAR(36) NOT NULL REFERENCES g4js_resource(id),
    subject_type VARCHAR(16) NOT NULL CHECK (subject_type IN ('USER','GROUP','AUTHENTICATED')),
    subject_id VARCHAR(255) NOT NULL,
    permission VARCHAR(32) NOT NULL,
    PRIMARY KEY(resource_id,subject_type,subject_id,permission)
);
CREATE INDEX idx_g4js_acl_subject ON g4js_acl(subject_type,subject_id,resource_id);
CREATE TABLE g4js_version (
    definition_schema_version INTEGER NOT NULL,
    format_adapter_id VARCHAR(128) NOT NULL,
    format_adapter_version VARCHAR(64) NOT NULL,
    id VARCHAR(36) PRIMARY KEY,
    chain_id VARCHAR(36) NOT NULL REFERENCES g4js_chain(resource_id),
    draft_id VARCHAR(36) NOT NULL REFERENCES g4js_draft(resource_id),
    revision_id VARCHAR(36) NOT NULL REFERENCES g4js_revision(id),
    revision_number BIGINT NOT NULL,
    runtime_assembly_line_id VARCHAR(64) NOT NULL,
    version_label VARCHAR(64) NOT NULL,
    author VARCHAR(255) NOT NULL,
    created_at BIGINT NOT NULL,
    runtime_context_id VARCHAR(255) NOT NULL,
    application_build VARCHAR(255) NOT NULL,
    catalog_fingerprint VARCHAR(255) NOT NULL,
    media_type VARCHAR(128) NOT NULL,
    artifact_hash VARCHAR(64) NOT NULL,
    source_text TEXT NOT NULL,
    UNIQUE(chain_id,version_label)
);
CREATE INDEX idx_g4js_version_chain ON g4js_version(chain_id,created_at,id);
CREATE TABLE g4js_publication (
    attempt_id VARCHAR(36) NOT NULL,
    version_id VARCHAR(36) NOT NULL REFERENCES g4js_version(id),
    execution_mode VARCHAR(8) NOT NULL CHECK (CASE execution_mode WHEN 'TEST' THEN TRUE WHEN 'RUN' THEN TRUE ELSE FALSE END),
    result_state VARCHAR(16) NOT NULL CHECK (CASE result_state WHEN 'REQUESTED' THEN TRUE WHEN 'CONFIRMED' THEN TRUE WHEN 'REJECTED' THEN TRUE WHEN 'UNKNOWN' THEN TRUE ELSE FALSE END),
    requested_by VARCHAR(255) NOT NULL,
    requested_at BIGINT NOT NULL,
    completed_at BIGINT,
    published_by VARCHAR(255),
    published_at BIGINT,
    error_code VARCHAR(64),
    PRIMARY KEY(version_id,execution_mode)
);
CREATE TABLE g4js_scenario (
    id VARCHAR(36) PRIMARY KEY,
    resource_id VARCHAR(36) NOT NULL REFERENCES g4js_resource(id),
    chain_id VARCHAR(36) NOT NULL REFERENCES g4js_chain(resource_id),
    display_name VARCHAR(160) NOT NULL,
    owner_subject VARCHAR(255) NOT NULL,
    entity_version BIGINT NOT NULL CHECK (entity_version > 0),
    head_number BIGINT NOT NULL CHECK (head_number > 0),
    archived BOOLEAN NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);
CREATE INDEX idx_g4js_scenario_scope ON g4js_scenario(resource_id,created_at,id);
CREATE TABLE g4js_scenario_revision (
    id VARCHAR(36) PRIMARY KEY,
    scenario_id VARCHAR(36) NOT NULL REFERENCES g4js_scenario(id),
    revision_number BIGINT NOT NULL CHECK (revision_number > 0),
    author VARCHAR(255) NOT NULL,
    saved_at BIGINT NOT NULL,
    data_json TEXT NOT NULL,
    data_hash VARCHAR(64) NOT NULL,
    UNIQUE(scenario_id,revision_number)
);
CREATE TABLE g4js_audit (
    scenario_id VARCHAR(36),
    scenario_revision_id VARCHAR(36),
    scenario_revision_number BIGINT,
    attempt_id VARCHAR(36),
    sequence_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL UNIQUE,
    domain_id VARCHAR(36) NOT NULL REFERENCES g4js_domain(resource_id),
    chain_id VARCHAR(36) REFERENCES g4js_chain(resource_id),
    draft_id VARCHAR(36) REFERENCES g4js_draft(resource_id),
    resource_kind VARCHAR(16) NOT NULL,
    resource_id VARCHAR(36) NOT NULL REFERENCES g4js_resource(id),
    resource_version BIGINT NOT NULL,
    actor VARCHAR(255) NOT NULL,
    action VARCHAR(32) NOT NULL,
    occurred_at BIGINT NOT NULL,
    revision_id VARCHAR(36) REFERENCES g4js_revision(id),
    request_id VARCHAR(36),
    version_id VARCHAR(36) REFERENCES g4js_version(id),
    access_before TEXT,
    access_after TEXT
);
CREATE INDEX idx_g4js_audit_domain ON g4js_audit(domain_id,sequence_id);
CREATE INDEX idx_g4js_audit_chain ON g4js_audit(chain_id,sequence_id);
CREATE INDEX idx_g4js_audit_draft ON g4js_audit(draft_id,sequence_id);
CREATE TABLE g4js_run (
    id VARCHAR(36) PRIMARY KEY,
    resource_id VARCHAR(36) NOT NULL REFERENCES g4js_resource(id),
    chain_id VARCHAR(36) NOT NULL REFERENCES g4js_chain(resource_id),
    version_id VARCHAR(36) REFERENCES g4js_version(id),
    engine_run_id VARCHAR(36),
    execution_mode VARCHAR(8) NOT NULL,
    result_state VARCHAR(24) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    stale_at BIGINT,
    started_at BIGINT,
    ended_at BIGINT,
    snapshot_json TEXT NOT NULL
);
CREATE INDEX idx_g4js_run_scope ON g4js_run(resource_id,created_at,id);
CREATE INDEX idx_g4js_run_chain ON g4js_run(chain_id,created_at,id);
CREATE INDEX idx_g4js_run_status ON g4js_run(chain_id,result_state,created_at,id);
CREATE INDEX idx_g4js_run_engine ON g4js_run(engine_run_id);
CREATE INDEX idx_g4js_run_retention ON g4js_run(ended_at,id);
CREATE INDEX idx_g4js_run_stale ON g4js_run(stale_at,updated_at,id);

CREATE TABLE g4js_simulation (
    id VARCHAR(36) PRIMARY KEY,
    scenario_id VARCHAR(36) NOT NULL REFERENCES g4js_scenario(id),
    created_at BIGINT NOT NULL,
    snapshot_json TEXT NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL
);
CREATE INDEX idx_g4js_simulation_scenario ON g4js_simulation(scenario_id,created_at,id);
CREATE INDEX idx_g4js_simulation_retention ON g4js_simulation(created_at,id);

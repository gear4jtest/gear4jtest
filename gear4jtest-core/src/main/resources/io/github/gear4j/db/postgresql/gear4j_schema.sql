CREATE TABLE IF NOT EXISTS pipeline_executions (
    id VARCHAR(36) PRIMARY KEY,
    pipeline_id VARCHAR(255) NOT NULL,
    input_parameters JSONB,
    context JSONB,
    result JSONB,
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    error_message TEXT
);

CREATE TABLE IF NOT EXISTS operation_executions (
    id VARCHAR(36) PRIMARY KEY,
    pipeline_execution_id VARCHAR(36) NOT NULL,
    operation_id VARCHAR(255) NOT NULL,
    parent_operation_id VARCHAR(36),
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    error_message TEXT,
    error_handler_messages TEXT,
    context JSONB,

    CONSTRAINT fk_pipeline_exec
    FOREIGN KEY (pipeline_execution_id)
    REFERENCES pipeline_executions(id)
    ON DELETE CASCADE,

    CONSTRAINT fk_parent_op
    FOREIGN KEY (parent_operation_id)
    REFERENCES operation_executions(id)
    ON DELETE CASCADE
);

-- Index pour accélérer les recherches par pipeline ou statut
CREATE INDEX IF NOT EXISTS idx_pe_pipeline_id ON pipeline_executions(pipeline_id);
CREATE INDEX IF NOT EXISTS idx_pe_status ON pipeline_executions(status);
CREATE INDEX IF NOT EXISTS idx_oe_pipeline_id ON operation_executions(pipeline_execution_id);

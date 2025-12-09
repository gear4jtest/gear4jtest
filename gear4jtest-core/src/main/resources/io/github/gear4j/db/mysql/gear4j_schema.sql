CREATE TABLE IF NOT EXISTS pipeline_executions (
    id VARCHAR(36) PRIMARY KEY,
    pipeline_id VARCHAR(255) NOT NULL,
    input_parameters JSON,
    context JSON,
    result JSON,
    status VARCHAR(50) NOT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6),
    error_message LONGTEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS operation_executions (
    id VARCHAR(36) PRIMARY KEY,
    pipeline_execution_id VARCHAR(36) NOT NULL,
    operation_id VARCHAR(255) NOT NULL,
    parent_operation_id VARCHAR(36),
    status VARCHAR(50) NOT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6),
    error_message LONGTEXT,
    error_handler_messages LONGTEXT,
    context JSON,

    CONSTRAINT fk_pipeline_exec
    FOREIGN KEY (pipeline_execution_id)
    REFERENCES pipeline_executions(id)
    ON DELETE CASCADE,

    CONSTRAINT fk_parent_op
    FOREIGN KEY (parent_operation_id)
    REFERENCES operation_executions(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_pe_pipeline_id ON pipeline_executions(pipeline_id);
CREATE INDEX idx_pe_status ON pipeline_executions(status);
CREATE INDEX idx_oe_pipeline_id ON operation_executions(pipeline_execution_id);

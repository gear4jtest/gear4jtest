CREATE TABLE IF NOT EXISTS assembly_run (
    id VARCHAR(36) PRIMARY KEY,
    pipeline_id VARCHAR(255) NOT NULL,
    input_parameters JSON,
    context JSON,
    result JSON,
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    error_message VARCHAR,
    parent_execution_id VARCHAR(36),
    root_execution_id VARCHAR(36),
    parent_station_log_id VARCHAR(36)
);

CREATE TABLE IF NOT EXISTS station_log (
    id VARCHAR(36) PRIMARY KEY,
    pipeline_execution_id VARCHAR(36) NOT NULL,
    operation_id VARCHAR(255) NOT NULL,
    parent_log_id VARCHAR(36),
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    error_message VARCHAR,
    error_handler_messages VARCHAR,
    context JSON,
    item_id VARCHAR(255),

    CONSTRAINT fk_pipeline_exec
    FOREIGN KEY (pipeline_execution_id)
    REFERENCES assembly_run(id)
    ON DELETE CASCADE,

    CONSTRAINT fk_parent_op
    FOREIGN KEY (parent_log_id)
    REFERENCES station_log(id)
    ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pe_pipeline_id ON assembly_run(pipeline_id);
CREATE INDEX IF NOT EXISTS idx_pe_status ON assembly_run(status);
CREATE INDEX IF NOT EXISTS idx_oe_pipeline_id ON station_log(pipeline_execution_id);

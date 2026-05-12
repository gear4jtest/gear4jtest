CREATE TABLE IF NOT EXISTS assembly_run
(
    id
    UUID
    PRIMARY
    KEY,
    pipeline_id
    VARCHAR
(
    255
) NOT NULL,
    input_parameters JSONB,
    context JSONB,
    result JSONB,
    status VARCHAR
(
    50
) NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    error_message TEXT,
    parent_execution_id UUID,
    root_execution_id UUID,
    parent_station_log_id UUID
    );

CREATE TABLE IF NOT EXISTS station_log
(
    id
    UUID
    PRIMARY
    KEY,
    pipeline_execution_id
    UUID
    NOT
    NULL,
    operation_id
    VARCHAR
(
    255
) NOT NULL,
    parent_log_id UUID,
    status VARCHAR
(
    50
) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    error_message TEXT,
    error_handler_messages TEXT,
    context JSONB,
    item_id VARCHAR
(
    255
),
    CONSTRAINT fk_pipeline_exec
    FOREIGN KEY
(
    pipeline_execution_id
)
    REFERENCES assembly_run
(
    id
)
    ON DELETE CASCADE,
    CONSTRAINT fk_parent_op
    FOREIGN KEY
(
    parent_log_id
)
    REFERENCES station_log
(
    id
)
    ON DELETE CASCADE
    );

-- Index pour accélérer les recherches par pipeline ou statut
CREATE INDEX IF NOT EXISTS idx_pe_pipeline_id ON assembly_run(pipeline_id);
CREATE INDEX IF NOT EXISTS idx_pe_status ON assembly_run(status);
CREATE INDEX IF NOT EXISTS idx_oe_pipeline_id ON station_log(pipeline_execution_id);

CREATE TABLE assembly_run
(
    id VARCHAR2(36) PRIMARY KEY,
    pipeline_id VARCHAR2(255) NOT NULL,
    input_parameters CLOB CHECK (input_parameters IS JSON),
    context CLOB CHECK (context IS JSON),
    result CLOB CHECK (result IS JSON),
    status VARCHAR2(50) NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    error_message CLOB,
    parent_execution_id VARCHAR2(36),
    root_execution_id VARCHAR2(36),
    parent_station_log_id VARCHAR2(36)
);

CREATE TABLE station_log
(
    id VARCHAR2(36) PRIMARY KEY,
    pipeline_execution_id VARCHAR2(36) NOT NULL,
    operation_id VARCHAR2(255) NOT NULL,
    parent_log_id VARCHAR2(36),
    branch_id VARCHAR2(255),
    status VARCHAR2(50) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    error_message CLOB,
    error_handler_messages CLOB,
    context CLOB CHECK (context IS JSON),
    item_id VARCHAR2(255),
    CONSTRAINT fk_pipeline_exec FOREIGN KEY (pipeline_execution_id)
        REFERENCES assembly_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_parent_op FOREIGN KEY (parent_log_id)
        REFERENCES station_log (id) ON DELETE CASCADE
);

CREATE INDEX idx_pe_pipeline_id ON assembly_run (pipeline_id);
CREATE INDEX idx_pe_status ON assembly_run (status);
CREATE INDEX idx_oe_pipeline_id ON station_log (pipeline_execution_id);
CREATE INDEX idx_station_log_exec_parent ON station_log (pipeline_execution_id, parent_log_id);

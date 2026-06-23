CREATE TABLE assembly_run
(
    id VARCHAR2(36) PRIMARY KEY,
    assembly_line_id VARCHAR2(255) NOT NULL,
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
    assembly_line_execution_id VARCHAR2(36) NOT NULL,
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
    CONSTRAINT fk_assembly_line_exec FOREIGN KEY (assembly_line_execution_id)
        REFERENCES assembly_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_parent_op FOREIGN KEY (parent_log_id)
        REFERENCES station_log (id) ON DELETE CASCADE
);

CREATE INDEX idx_ar_assembly_line_id ON assembly_run (assembly_line_id);
CREATE INDEX idx_ar_status ON assembly_run (status);
CREATE INDEX idx_sl_assembly_line_execution_id ON station_log (assembly_line_execution_id);
CREATE INDEX idx_station_log_exec_parent ON station_log (assembly_line_execution_id, parent_log_id);
CREATE INDEX idx_station_log_run_start ON station_log (assembly_line_execution_id, start_time, id);
CREATE INDEX idx_ar_assembly_line_start ON assembly_run (assembly_line_id, start_time);
CREATE INDEX idx_ar_status_start ON assembly_run (status, start_time);

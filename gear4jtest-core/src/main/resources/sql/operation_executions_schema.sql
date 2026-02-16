CREATE TABLE station_log (
    id VARCHAR(255) PRIMARY KEY,
    pipeline_execution_id VARCHAR(255) NOT NULL,
    operation_id VARCHAR(255) NOT NULL,
    parent_log_id VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    error_message TEXT,
    error_handler_messages TEXT,
    context TEXT,
    FOREIGN KEY (pipeline_execution_id) REFERENCES assembly_run(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_log_id) REFERENCES station_log(id) ON DELETE CASCADE
);

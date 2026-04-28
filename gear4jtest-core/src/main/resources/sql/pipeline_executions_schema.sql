CREATE TABLE assembly_run (
    id VARCHAR(255) PRIMARY KEY,
    pipeline_id VARCHAR(255) NOT NULL,
    input_parameters TEXT,
    context TEXT,
    result TEXT,
    status VARCHAR(50) NOT NULL,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    error_message TEXT,
    parent_execution_id VARCHAR(255),
    root_execution_id VARCHAR(255),
    parent_station_log_id VARCHAR(255)
);

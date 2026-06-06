CREATE INDEX IF NOT EXISTS idx_station_log_exec_parent ON station_log(pipeline_execution_id, parent_log_id);

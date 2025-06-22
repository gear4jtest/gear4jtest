package io.github.gear4jtest.core.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.model.refactor.OperationExecution;

public class DatabasePipelineExecutionRepository implements PipelineExecutionRepository {
    private final DataSource dataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatabasePipelineExecutionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void initialize() {
        boolean doesPipelineExecutionTableExist = false;
        boolean doesOperationExecutionTableExist = false;
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData databaseMetaData = conn.getMetaData();
            ResultSet resultSet = databaseMetaData.getTables(null, null, null, new String[] {"TABLE"});

            while (resultSet.next()) {
                String name = resultSet.getString("TABLE_NAME");
                String schema = resultSet.getString("TABLE_SCHEM");
                System.out.println(name + " on schema " + schema);

                if (name.equals("pipeline_executions")) {
                    doesPipelineExecutionTableExist = true;
                } else if (name.equals("operation_executions")) {
                    doesOperationExecutionTableExist = true;
                }
            }
            if (!doesPipelineExecutionTableExist) {
                createPipelineExecutionTable(conn, "/sql/pipeline_executions_schema.sql");
            }
            if (!doesOperationExecutionTableExist) {
                createPipelineExecutionTable(conn, "/sql/operation_executions_schema.sql");
            }
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void createPipelineExecutionTable(Connection conn, String schemaPath) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(schemaPath)) {
            String createPipelineExecutionSQL = new String(is.readAllBytes());

            try (PreparedStatement stmt1 = conn.prepareStatement(createPipelineExecutionSQL)) {
                stmt1.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Error creating tables", e);
            }
        }
    }

    @Override
    public void save(PipelineExecution execution) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO pipeline_executions (id, pipeline_id, input_parameters, context, result, status, start_time, end_time, error_message) VALUES (?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, execution.getId().toString());
                stmt.setString(2, execution.getPipelineId());
                stmt.setString(3, toJson(execution.getInputParams()));
                stmt.setString(4, toJson(execution.getContext()));
                stmt.setString(5, toJson(execution.getResult()));
                stmt.setString(6, execution.getStatus().name());
                stmt.setTimestamp(7, Timestamp.from(execution.getStartTime()));
                stmt.setTimestamp(8, execution.getEndTime() != null ? Timestamp.from(execution.getEndTime()) : null);
                stmt.setString(9, execution.getErrorMessage());
                stmt.executeUpdate();
            }
            saveOperations(conn, execution.getOperations(), execution.getId().toString(), null);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveOperations(Connection conn, List<OperationExecutionRecord> operations, String pipelineId, String parentId) throws SQLException {
        String sql = "INSERT INTO operation_executions (id, pipeline_execution_id, operation_id, parent_operation_id, status, start_time, end_time, error_message, error_handler_messages, context) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (OperationExecutionRecord op : operations) {
                stmt.setString(1, op.getId());
                stmt.setString(2, pipelineId);
                stmt.setString(3, op.getOperationId());
                stmt.setString(4, parentId);
                stmt.setString(5, op.getStatus().toString());
                stmt.setTimestamp(6, Timestamp.from(op.getStartTime()));
                stmt.setTimestamp(7, Timestamp.from(op.getEndTime()));
                stmt.setString(8, op.getErrorMessage());
                stmt.setString(9, op.getErrorHandlerMessages());
                stmt.setString(10, toJson(op.getContext()));
                stmt.addBatch();
            }
            stmt.executeBatch();
            for (OperationExecutionRecord op : operations) {
                if (!op.getSubOperations().isEmpty()) {
                    saveOperations(conn, op.getSubOperations(), pipelineId, op.getId());
                }
            }
        }
    }

    @Override
    public void update(PipelineExecution execution) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE pipeline_executions SET context=?, result=?, status=?, end_time=?, error_message=? WHERE id=?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, toJson(execution.getContext()));
                stmt.setString(2, toJson(execution.getResult()));
                stmt.setString(3, execution.getStatus().name());
                stmt.setTimestamp(4, execution.getEndTime() != null ? Timestamp.from(execution.getEndTime()) : null);
                stmt.setString(5, execution.getErrorMessage());
                stmt.setString(6, execution.getId().toString());
                stmt.executeUpdate();
            }
            saveOperations(conn, execution.getOperations(), execution.getId().toString(), null);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<PipelineExecution> findById(UUID id) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM pipeline_executions WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, id.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        PipelineExecution exec = mapExecution(rs);
                        exec.setOperations(loadOperations(conn, id.toString(), null));
                        return Optional.of(exec);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    private List<OperationExecutionRecord> loadOperations(Connection conn, String pipelineId, String parentId) throws SQLException {
        String sql = "SELECT * FROM operation_executions WHERE pipeline_execution_id = ? AND " +
                (parentId == null ? "parent_operation_id IS NULL" : "parent_operation_id = ?") + " ORDER BY start_time";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pipelineId);
            if (parentId != null) stmt.setString(2, parentId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<OperationExecutionRecord> operations = new ArrayList<>();
                while (rs.next()) {
                    OperationExecutionRecord op = mapOperation(rs);
                    op.setSubOperations(loadOperations(conn, pipelineId, op.getId()));
                    operations.add(op);
                }
                return operations;
            }
        }
    }

    @Override
    public List<PipelineExecution> findByPipelineId(String pipelineId) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM pipeline_executions WHERE pipeline_id = ? ORDER BY start_time DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, pipelineId);
                return executeQuery(stmt);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<PipelineExecution> findByStatus(ExecutionStatus status) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM pipeline_executions WHERE status = ? ORDER BY start_time DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, status.name());
                return executeQuery(stmt);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(String id) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt1 = conn.prepareStatement("DELETE FROM operation_executions WHERE pipeline_execution_id = ?");
                 PreparedStatement stmt2 = conn.prepareStatement("DELETE FROM pipeline_executions WHERE id = ?")) {
                stmt1.setString(1, id);
                stmt1.executeUpdate();
                stmt2.setString(1, id);
                stmt2.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<PipelineExecution> findAll() {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM pipeline_executions";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                return executeQuery(stmt);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<PipelineExecution> executeQuery(PreparedStatement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            List<PipelineExecution> executions = new ArrayList<>();
            while (rs.next()) {
                executions.add(mapExecution(rs));
            }
            return executions;
        }
    }

    private PipelineExecution mapExecution(ResultSet rs) throws SQLException {
        PipelineExecution exec = new PipelineExecution();
        exec.setId(UUID.fromString(rs.getString("id")));
        exec.setPipelineId(rs.getString("pipeline_id"));
        exec.setInputParams(fromJson(rs.getString("input_parameters"), Map.class));
        exec.setContext(fromJson(rs.getString("context"), Map.class));
        exec.setResult(fromJson(rs.getString("result"), Object.class));
        exec.setStatus(ExecutionStatus.valueOf(rs.getString("status")));
        exec.setStartTime(rs.getTimestamp("start_time").toInstant());
        Timestamp endTs = rs.getTimestamp("end_time");
        exec.setEndTime(endTs != null ? endTs.toInstant() : null);
        exec.setErrorMessage(rs.getString("error_message"));
        return exec;
    }

    private OperationExecutionRecord mapOperation(ResultSet rs) throws SQLException {
        OperationExecutionRecord op = new OperationExecutionRecord();
        op.setId(rs.getString("id"));
        op.setPipelineExecutionId(rs.getString("pipeline_execution_id"));
        op.setOperationId(rs.getString("operation_id"));
        op.setParentOperationId(rs.getString("parent_operation_id"));
        op.setStatus(OperationExecution.OperationReport.Status.valueOf(rs.getString("status")));
        op.setStartTime(rs.getTimestamp("start_time").toInstant());
        op.setEndTime(rs.getTimestamp("end_time").toInstant());
        op.setErrorMessage(rs.getString("error_message"));
        op.setErrorHandlerMessages(rs.getString("error_handler_messages"));
        op.setContext(fromJson(rs.getString("context"), Map.class));
        return op;
    }

    private String toJson(Object obj) {
        try {
            return obj != null ? objectMapper.writeValueAsString(obj) : null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return json != null ? objectMapper.readValue(json, clazz) : null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

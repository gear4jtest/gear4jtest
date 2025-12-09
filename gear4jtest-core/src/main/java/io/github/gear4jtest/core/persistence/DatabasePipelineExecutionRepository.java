package io.github.gear4jtest.core.persistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DatabasePipelineExecutionRepository implements PipelineExecutionRepository {

    private static final String SCRIPT_POSTGRES = "/io/github/gear4j/db/postgresql/gear4j_schema.sql";
    private static final String SCRIPT_MYSQL = "/io/github/gear4j/db/mysql/gear4j_schema.sql";
    private static final String SCRIPT_H2 = "/io/github/gear4j/db/h2/gear4j_schema.sql";

    private final DataSource dataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatabasePipelineExecutionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void initialize() {
        try (Connection conn = dataSource.getConnection()) {
            if (isSchemaInitialized(conn)) {
                return;
            }

            String scriptPath = resolveScriptPath(conn);
            System.out.println("[Gear4J] Initializing schema using script: " + scriptPath);
            executeScript(conn, scriptPath);
        } catch (SQLException | IOException e) {
            throw new RuntimeException("Error while initializing Gear4J schema", e);
        }
    }

    /**
     * Vérifie simplement si la table principale 'pipeline_executions' existe déjà.
     * C'est le marqueur pour savoir si on doit lancer le script ou non.
     */
    private boolean isSchemaInitialized(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();

        // On teste en minuscule et majuscule car selon les DB ça change
        return tableExists(meta, "pipeline_executions") || tableExists(meta, "PIPELINE_EXECUTIONS");
    }

    private boolean tableExists(DatabaseMetaData meta, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    /**
     * Détecte le type de base de données et retourne le bon script SQL.
     */
    private String resolveScriptPath(Connection conn) throws SQLException {
        String dbProductName = conn.getMetaData().getDatabaseProductName().toLowerCase();

        if (dbProductName.contains("postgresql")) {
            return SCRIPT_POSTGRES;
        } else if (dbProductName.contains("mysql") || dbProductName.contains("mariadb")) {
            return SCRIPT_MYSQL;
        } else if (dbProductName.contains("h2")) {
            return SCRIPT_H2;
        } else {
            // Fallback par défaut (H2) ou Exception selon ta politique
            System.err.println("[Gear4J] Unknown database '" + dbProductName + "'. Falling back to H2 script.");
            return SCRIPT_H2;
        }
    }

    /**
     * Lit et exécute le script SQL statement par statement.
     */
    private void executeScript(Connection conn, String scriptPath) throws IOException, SQLException {
        try (InputStream is = getClass().getResourceAsStream(scriptPath)) {
            if (is == null) {
                throw new IOException("Script file not found in classpath: " + scriptPath);
            }

            // Lecture du fichier complet
            String scriptContent;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                scriptContent = reader.lines().collect(Collectors.joining("\n"));
            }

            // Découpage naïf par point-virgule pour exécuter instruction par instruction
            // (Tes scripts sont simples, donc ça suffit largement)
            String[] statements = scriptContent.split(";");

            try (Statement stmt = conn.createStatement()) {
                for (String sql : statements) {
                    String trimmedSql = sql.trim();
                    if (trimmedSql.isEmpty()) {
                        continue;
                    }
                    stmt.execute(trimmedSql);
                }
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
                stmt.setTimestamp(6, Timestamp.from(op.getStartedAt()));
                stmt.setTimestamp(7, Timestamp.from(op.getEndedAt()));
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

    /**
     * Sauvegarde un batch de OperationExecutionRecord en une seule passe.
     * Utilisé par DatabaseExecutionManager pour limiter le nombre de transactions.
     */
    public void saveOperationsBatch(List<OperationExecutionRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO operation_executions " +
                "(id, pipeline_execution_id, operation_id, parent_operation_id, status, " +
                " start_time, end_time, error_message, error_handler_messages, context) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (OperationExecutionRecord rec : records) {
                stmt.setString(1, rec.getId());
                stmt.setString(2, rec.getPipelineExecutionId());
                stmt.setString(3, rec.getOperationId());
                stmt.setString(4, rec.getParentOperationId());
                stmt.setString(5, rec.getStatus().toString());
                stmt.setTimestamp(6, Timestamp.from(rec.getStartedAt()));
                stmt.setTimestamp(7, rec.getEndedAt() != null ? Timestamp.from(rec.getEndedAt()) : null);
                stmt.setString(8, rec.getErrorMessage());
                stmt.setString(9, rec.getErrorHandlerMessages());
                stmt.setString(10, toJson(rec.getContext()));
                stmt.addBatch();
            }

            stmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(UUID id) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt1 = conn.prepareStatement("DELETE FROM operation_executions WHERE pipeline_execution_id = ?");
                 PreparedStatement stmt2 = conn.prepareStatement("DELETE FROM pipeline_executions WHERE id = ?")) {
                stmt1.setString(1, id.toString());
                stmt1.executeUpdate();
                stmt2.setString(1, id.toString());
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
        op.setStatus(OperationExecutionRecord.Status.valueOf(rs.getString("status")));
        op.setStartedAt(rs.getTimestamp("start_time").toInstant());
        op.setEndedAt(rs.getTimestamp("end_time").toInstant());
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

    public void saveOperation(io.github.gear4jtest.core.persistence.OperationExecutionRecord rec) {
        String sql = "INSERT INTO operation_executions (id, pipeline_execution_id, operation_id, parent_operation_id, status, start_time, end_time, error_message, error_handler_messages, context) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (java.sql.Connection conn = dataSource.getConnection(); java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rec.getId());
            ps.setString(2, rec.getPipelineExecutionId());
            ps.setString(3, rec.getOperationId());
            ps.setString(4, rec.getParentOperationId());
            ps.setString(5, rec.getStatus().toString());
            ps.setTimestamp(6, java.sql.Timestamp.from(rec.getStartedAt()));
            ps.setTimestamp(7, rec.getEndedAt() != null ? java.sql.Timestamp.from(rec.getEndedAt()) : null);
            ps.setString(8, rec.getErrorMessage());
            ps.setString(9, rec.getErrorHandlerMessages());
            ps.setString(10, toJson(rec.getContext()));
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    
    public static String toJson(java.util.Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

}

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
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DatabaseAssemblyRunRepository implements AssemblyRunRepository {

    private static final String SCRIPT_POSTGRES = "/io/github/gear4j/db/postgresql/gear4j_schema.sql";
    private static final String SCRIPT_MYSQL = "/io/github/gear4j/db/mysql/gear4j_schema.sql";
    private static final String SCRIPT_H2 = "/io/github/gear4j/db/h2/gear4j_schema.sql";

    private final DataSource dataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DatabaseAssemblyRunRepository(DataSource dataSource) {
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
     * Vérifie simplement si la table principale 'assembly_run' existe déjà.
     * C'est le marqueur pour savoir si on doit lancer le script ou non.
     */
    private boolean isSchemaInitialized(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();

        // On teste en minuscule et majuscule car selon les DB ça change
        return tableExists(meta, "assembly_run") || tableExists(meta, "ASSEMBLY_RUN");
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
                scriptContent = reader.lines().collect(Collectors.joining(""));
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
    public void save(AssemblyRun execution) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "INSERT INTO assembly_run (id, pipeline_id, input_parameters, context, result, status, start_time, end_time, error_message) VALUES (?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, execution.getId());
                stmt.setString(2, execution.getPipelineId());
                stmt.setObject(3, toJson(execution.getInputParams()), Types.OTHER);
                stmt.setObject(4, toJson(execution.getContext()), Types.OTHER);
                stmt.setObject(5, toJson(execution.getResult()), Types.OTHER);
                stmt.setString(6, execution.getStatus().name());
                stmt.setTimestamp(7, execution.getStartTime() != null ? Timestamp.from(execution.getStartTime()) : null);
                stmt.setTimestamp(8, execution.getEndTime() != null ? Timestamp.from(execution.getEndTime()) : null);
                stmt.setString(9, execution.getErrorMessage());
                stmt.executeUpdate();
            }
            saveOperations(conn, execution.getOperations(), execution.getId().toString(), null);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveOperations(Connection conn, List<StationLog> operations, String pipelineId, UUID parentId) throws SQLException {
        String sql = "INSERT INTO station_log (id, pipeline_execution_id, operation_id, parent_log_id, status, start_time, end_time, error_message, error_handler_messages, context) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (StationLog op : operations) {
                stmt.setObject(1, op.getId());
                stmt.setString(2, pipelineId);
                stmt.setString(3, op.getOperationId());
                stmt.setObject(4, parentId);
                stmt.setString(5, op.getStatus().toString());
                stmt.setTimestamp(6, Timestamp.from(op.getStartedAt()));
                stmt.setTimestamp(7, Timestamp.from(op.getEndedAt()));
                stmt.setString(8, op.getErrorMessage());
                stmt.setString(9, op.getErrorHandlerMessages());
                stmt.setString(10, toJson(op.getContext()));
                stmt.addBatch();
            }
            stmt.executeBatch();
            for (StationLog op : operations) {
                if (!op.getSubOperations().isEmpty()) {
                    saveOperations(conn, op.getSubOperations(), pipelineId, op.getId());
                }
            }
        }
    }

    @Override
    public void update(AssemblyRun execution) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "UPDATE assembly_run SET context=?, result=?, status=?, end_time=?, error_message=? WHERE id=?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, toJson(execution.getContext()), Types.OTHER);
                stmt.setObject(2, toJson(execution.getResult()), Types.OTHER);
                stmt.setString(3, execution.getStatus().name());
                stmt.setTimestamp(4, execution.getEndTime() != null ? Timestamp.from(execution.getEndTime()) : null);
                stmt.setString(5, execution.getErrorMessage());
                stmt.setObject(6, execution.getId());
                stmt.executeUpdate();
            }
            saveOperations(conn, execution.getOperations(), execution.getId().toString(), null);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<AssemblyRun> findById(UUID id) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM assembly_run WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        AssemblyRun exec = mapExecution(rs);
                        exec.setOperations(loadOperations(conn, id, null));
                        return Optional.of(exec);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    private List<StationLog> loadOperations(Connection conn, UUID pipelineExecutionId, UUID parentId) throws SQLException {
        String sql = "SELECT * FROM station_log WHERE pipeline_execution_id = ? AND " +
                (parentId == null ? "parent_log_id IS NULL" : "parent_log_id = ?") + " ORDER BY start_time";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, pipelineExecutionId);
            if (parentId != null) stmt.setObject(2, parentId);
            try (ResultSet rs = stmt.executeQuery()) {
                List<StationLog> operations = new ArrayList<>();
                while (rs.next()) {
                    StationLog op = mapOperation(rs);
                    op.setSubOperations(loadOperations(conn, pipelineExecutionId, op.getId()));
                    operations.add(op);
                }
                return operations;
            }
        }
    }

    @Override
    public List<AssemblyRun> findByPipelineId(String pipelineId) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM assembly_run WHERE pipeline_id = ? ORDER BY start_time DESC";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, pipelineId);
                return executeQuery(stmt);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<AssemblyRun> findByStatus(ExecutionStatus status) {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM assembly_run WHERE status = ? ORDER BY start_time DESC";
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
    public void saveOperationsBatch(List<StationLog> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO station_log " +
                "(id, pipeline_execution_id, operation_id, parent_log_id, status, " +
                " start_time, end_time, error_message, error_handler_messages, context) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?)" +
                "ON CONFLICT (id) DO UPDATE SET" +
                "  status = EXCLUDED.status," +
                "  end_time = EXCLUDED.end_time," +
                "  error_message = EXCLUDED.error_message," +
                "  error_handler_messages = EXCLUDED.error_handler_messages," +
                "  context = EXCLUDED.context " +
                "WHERE station_log.end_time IS NULL;";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (StationLog rec : records) {
                stmt.setObject(1, rec.getId());
                stmt.setObject(2, rec.getPipelineExecutionId());
                stmt.setString(3, rec.getOperationId());
                stmt.setObject(4, rec.getParentOperationId());
                stmt.setString(5, rec.getStatus().toString());
                stmt.setTimestamp(6, Timestamp.from(rec.getStartedAt()));
                stmt.setTimestamp(7, rec.getEndedAt() != null ? Timestamp.from(rec.getEndedAt()) : null);
                stmt.setString(8, rec.getErrorMessage());
                stmt.setString(9, rec.getErrorHandlerMessages());
                stmt.setObject(10, toJson(rec.getContext()), Types.OTHER);
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
            try (PreparedStatement stmt1 = conn.prepareStatement("DELETE FROM station_log WHERE pipeline_execution_id = ?");
                 PreparedStatement stmt2 = conn.prepareStatement("DELETE FROM assembly_run WHERE id = ?")) {
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
    public List<AssemblyRun> findAll() {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM assembly_run";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                return executeQuery(stmt);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<AssemblyRun> executeQuery(PreparedStatement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery()) {
            List<AssemblyRun> executions = new ArrayList<>();
            while (rs.next()) {
                executions.add(mapExecution(rs));
            }
            return executions;
        }
    }

    private AssemblyRun mapExecution(ResultSet rs) throws SQLException {
        AssemblyRun exec = new AssemblyRun();
        exec.setId(UUID.fromString(rs.getString("id")));
        exec.setPipelineId(rs.getString("pipeline_id"));
        exec.setInputParams(fromJson(rs.getString("input_parameters"), Map.class));
        exec.setContext(fromJson(rs.getString("context"), Map.class));
        exec.setResult(fromJson(rs.getString("result"), Object.class));
        exec.setStatus(ExecutionStatus.valueOf(rs.getString("status")));
        Timestamp startTs = rs.getTimestamp("start_time");
        exec.setStartTime(startTs != null ? startTs.toInstant() : null);
        Timestamp endTs = rs.getTimestamp("end_time");
        exec.setEndTime(endTs != null ? endTs.toInstant() : null);
        exec.setErrorMessage(rs.getString("error_message"));
        return exec;
    }

    private StationLog mapOperation(ResultSet rs) throws SQLException {
        StationLog op = new StationLog();
        op.setId(rs.getObject("id", UUID.class));
        op.setPipelineExecutionId(rs.getObject("pipeline_execution_id", UUID.class));
        op.setOperationId(rs.getString("operation_id"));
        op.setParentOperationId(rs.getObject("parent_log_id", UUID.class));
        op.setStatus(StationLog.Status.valueOf(rs.getString("status")));
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

    public void saveOperation(StationLog rec) {
        String sql = "INSERT INTO station_log (id, pipeline_execution_id, operation_id, parent_log_id, status, start_time, end_time, error_message, error_handler_messages, context) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (java.sql.Connection conn = dataSource.getConnection(); java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, rec.getId());
            ps.setObject(2, rec.getPipelineExecutionId());
            ps.setString(3, rec.getOperationId());
            ps.setObject(4, rec.getParentOperationId());
            ps.setString(5, rec.getStatus().toString());
            ps.setTimestamp(6, java.sql.Timestamp.from(rec.getStartedAt()));
            ps.setTimestamp(7, rec.getEndedAt() != null ? java.sql.Timestamp.from(rec.getEndedAt()) : null);
            ps.setString(8, rec.getErrorMessage());
            ps.setString(9, rec.getErrorHandlerMessages());
            ps.setString(10, toJson(rec.getContext()));
            ps.executeUpdate();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    
    public static String toJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public void saveOperationSnapshotsBatch(List<StationLogSnapshot> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO station_log " +
                "(id, pipeline_execution_id, operation_id, parent_log_id, status, " +
                " start_time, end_time, error_message, error_handler_messages, context) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?)" +
                "ON CONFLICT (id) DO UPDATE SET" +
                "  status = EXCLUDED.status," +
                "  end_time = EXCLUDED.end_time," +
                "  error_message = EXCLUDED.error_message," +
                "  error_handler_messages = EXCLUDED.error_handler_messages," +
                "  context = EXCLUDED.context " +
                "WHERE station_log.end_time IS NULL;";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (StationLogSnapshot rec : records) {
                stmt.setObject(1, rec.id());
                stmt.setObject(2, rec.pipelineExecutionId());
                stmt.setString(3, rec.operationId());
                stmt.setObject(4, rec.parentOperationId());
                stmt.setString(5, rec.status().toString());
                stmt.setTimestamp(6, Timestamp.from(rec.startedAt()));
                stmt.setTimestamp(7, rec.endedAt() != null ? Timestamp.from(rec.endedAt()) : null);
                stmt.setString(8, rec.errorMessage());
                stmt.setString(9, rec.errorHandlerMessages());
                stmt.setObject(10, toJson(rec.context()), Types.OTHER);
                stmt.addBatch();
            }

            stmt.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

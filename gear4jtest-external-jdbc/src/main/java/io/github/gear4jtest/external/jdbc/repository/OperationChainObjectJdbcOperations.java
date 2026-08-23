package io.github.gear4jtest.external.jdbc.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainObjectCursor;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions;

final class OperationChainObjectJdbcOperations {
    private static final String OBJECT_COLUMNS = "id, al_id, version, publication_mode, content_hash, size_bytes, "
            + "mime_type, created_at, created_by, published_at";
    private static final PageRequest FIRST_ROW = PageRequest.first(1);

    private final Gear4jDatabaseDialect databaseDialect;
    private final JdbcStatementOptions statementOptions;
    private final OperationChainObjectRowMapper rowMapper;

    OperationChainObjectJdbcOperations(Gear4jDatabaseDialect databaseDialect,
                                       JdbcStatementOptions statementOptions,
                                       OperationChainObjectRowMapper rowMapper) {
        this.databaseDialect = Objects.requireNonNull(databaseDialect, "databaseDialect must not be null");
        this.statementOptions = Objects.requireNonNull(statementOptions, "statementOptions must not be null");
        this.rowMapper = Objects.requireNonNull(rowMapper, "rowMapper must not be null");
    }

    long insert(Connection connection, OperationChainObject object) throws SQLException {
        String sql = "INSERT INTO operation_chain_object(al_id, version, publication_mode, content_hash, size_bytes, "
                + "mime_type, created_at, created_by, published_at) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = prepareGeneratedKeyInsert(connection, sql)) {
            statement.setString(1, object.alId());
            statement.setString(2, object.version());
            ExternalRepositorySqlDialect.bindExecutionMode(databaseDialect, statement, 3, object.mode());
            statement.setString(4, OperationChainObjectRowMapper.requireContentHash(object.contentHash()));
            statement.setLong(5, object.sizeBytes());
            statement.setString(6, object.mimeType());
            databaseDialect.setInstant(statement, 7, object.createdAt());
            statement.setString(8, object.createdBy());
            databaseDialect.setInstant(statement, 9, object.publishedAt());
            int insertedRows = statement.executeUpdate();
            if (insertedRows != 1) {
                throw new SQLException("Expected to insert one operation-chain object but inserted " + insertedRows);
            }
            try (var generatedKeys = statement.getGeneratedKeys()) {
                return generatedKeys.next() ? generatedKeys.getLong(1) : -1L;
            }
        }
    }

    Optional<OperationChainObject> find(Connection connection,
                                        String assemblyLineId,
                                        String version,
                                        ExecutionMode mode)
            throws SQLException {
        String sql = "SELECT " + OBJECT_COLUMNS
                + " FROM operation_chain_object WHERE al_id=? AND version=? AND publication_mode=?";
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, assemblyLineId);
            statement.setString(2, version);
            ExternalRepositorySqlDialect.bindExecutionMode(databaseDialect, statement, 3, mode);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(rowMapper.mapObject(resultSet)) : Optional.empty();
            }
        }
    }

    Optional<OperationChainObject> findLatestRun(Connection connection, String assemblyLineId) throws SQLException {
        String orderedSql = "SELECT " + OBJECT_COLUMNS
                + " FROM operation_chain_object WHERE al_id=? AND publication_mode='RUN' "
                + "ORDER BY published_at DESC, id DESC";
        String sql = ExternalRepositorySqlDialect.pagedSql(databaseDialect, orderedSql);
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, assemblyLineId);
            ExternalRepositorySqlDialect.bindPage(databaseDialect, statement, 2, FIRST_ROW);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(rowMapper.mapObject(resultSet)) : Optional.empty();
            }
        }
    }

    boolean exists(Connection connection, String assemblyLineId, String version, ExecutionMode mode)
            throws SQLException {
        String sql = "SELECT 1 FROM operation_chain_object WHERE al_id=? AND version=? AND publication_mode=?";
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, assemblyLineId);
            statement.setString(2, version);
            ExternalRepositorySqlDialect.bindExecutionMode(databaseDialect, statement, 3, mode);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    List<OperationChainObject> findAll(Connection connection, String assemblyLineId, PageRequest pageRequest)
            throws SQLException {
        String orderedSql = "SELECT " + OBJECT_COLUMNS
                + " FROM operation_chain_object WHERE al_id=? ORDER BY published_at DESC, id DESC";
        String sql = ExternalRepositorySqlDialect.pagedSql(databaseDialect, orderedSql);
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, assemblyLineId);
            ExternalRepositorySqlDialect.bindPage(databaseDialect, statement, 2, pageRequest);
            return readObjects(statement);
        }
    }

    List<OperationChainObject> findAllAfter(Connection connection,
                                            String assemblyLineId,
                                            OperationChainObjectCursor after,
                                            int limit)
            throws SQLException {
        PageRequest pageRequest = PageRequest.first(limit);
        String cursorPredicate = after == null ? ""
                : " AND (published_at < ? OR (published_at = ? AND id < ?))";
        String orderedSql = "SELECT " + OBJECT_COLUMNS
                + " FROM operation_chain_object WHERE al_id=?" + cursorPredicate
                + " ORDER BY published_at DESC, id DESC";
        String sql = ExternalRepositorySqlDialect.pagedSql(databaseDialect, orderedSql);
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, assemblyLineId);
            int pageParameterIndex = 2;
            if (after != null) {
                databaseDialect.setInstant(statement, 2, after.publishedAt());
                databaseDialect.setInstant(statement, 3, after.publishedAt());
                statement.setLong(4, after.id());
                pageParameterIndex = 5;
            }
            ExternalRepositorySqlDialect.bindPage(databaseDialect, statement, pageParameterIndex, pageRequest);
            return List.copyOf(readObjects(statement));
        }
    }

    private List<OperationChainObject> readObjects(PreparedStatement statement) throws SQLException {
        try (var resultSet = statement.executeQuery()) {
            List<OperationChainObject> objects = new ArrayList<>();
            while (resultSet.next()) {
                objects.add(rowMapper.mapObject(resultSet));
            }
            return objects;
        }
    }

    private PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        return statementOptions.prepare(connection, sql);
    }

    private PreparedStatement prepareGeneratedKeyInsert(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = ExternalRepositorySqlDialect.prepareGeneratedKeyInsert(databaseDialect,
                                                                                             connection,
                                                                                             sql);
        statementOptions.apply(statement);
        return statement;
    }
}

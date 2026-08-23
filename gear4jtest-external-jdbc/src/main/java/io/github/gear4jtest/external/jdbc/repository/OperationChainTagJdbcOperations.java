package io.github.gear4jtest.external.jdbc.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions;

final class OperationChainTagJdbcOperations {
    private final Gear4jDatabaseDialect databaseDialect;
    private final JdbcStatementOptions statementOptions;

    OperationChainTagJdbcOperations(Gear4jDatabaseDialect databaseDialect,
                                    JdbcStatementOptions statementOptions) {
        this.databaseDialect = Objects.requireNonNull(databaseDialect, "databaseDialect must not be null");
        this.statementOptions = Objects.requireNonNull(statementOptions, "statementOptions must not be null");
    }

    List<String> findStageTags(Connection connection, String stageId) throws SQLException {
        String sql = "SELECT tag FROM operation_chain_publication_stage_tag WHERE stage_id=? ORDER BY tag";
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, stageId);
            try (var resultSet = statement.executeQuery()) {
                List<String> tags = new ArrayList<>();
                while (resultSet.next()) {
                    tags.add(resultSet.getString(1));
                }
                return List.copyOf(tags);
            }
        }
    }

    void insertStageTags(Connection connection, String stageId, List<String> tags) throws SQLException {
        if (tags.isEmpty()) {
            return;
        }
        String sql = ExternalRepositorySqlDialect.insertPublicationStageTagIfAbsentSql(databaseDialect);
        try (PreparedStatement statement = prepare(connection, sql)) {
            for (String tag : tags) {
                statement.setString(1, stageId);
                statement.setString(2, tag);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    void insertObjectTags(Connection connection, String assemblyLineId, List<String> tags) throws SQLException {
        if (tags.isEmpty()) {
            return;
        }
        String sql = ExternalRepositorySqlDialect.insertTagIfAbsentSql(databaseDialect);
        try (PreparedStatement statement = prepare(connection, sql)) {
            for (String tag : tags) {
                statement.setString(1, assemblyLineId);
                statement.setString(2, tag);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    void deleteStageTags(Connection connection, String stageId) throws SQLException {
        String sql = "DELETE FROM operation_chain_publication_stage_tag WHERE stage_id=?";
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, stageId);
            statement.executeUpdate();
        }
    }

    private PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        return statementOptions.prepare(connection, sql);
    }
}

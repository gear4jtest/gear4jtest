package io.github.gear4jtest.external.jdbc.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationStage;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationStageCursor;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions;

final class OperationChainPublicationStageJdbcOperations {
    private static final String STAGE_COLUMNS = "stage_id, al_id, version, publication_mode, content_hash, "
            + "size_bytes, mime_type, created_at, created_by, published_at, store_fingerprint, staged_at, "
            + "stage_revision";
    private static final String PAGED_STAGE_COLUMNS = "staged_page."
            + STAGE_COLUMNS.replace(", ", ", staged_page.");

    private final Gear4jDatabaseDialect databaseDialect;
    private final JdbcStatementOptions statementOptions;
    private final OperationChainObjectRowMapper rowMapper;
    private final OperationChainTagJdbcOperations tagOperations;

    OperationChainPublicationStageJdbcOperations(Gear4jDatabaseDialect databaseDialect,
                                                 JdbcStatementOptions statementOptions,
                                                 OperationChainObjectRowMapper rowMapper,
                                                 OperationChainTagJdbcOperations tagOperations) {
        this.databaseDialect = Objects.requireNonNull(databaseDialect, "databaseDialect must not be null");
        this.statementOptions = Objects.requireNonNull(statementOptions, "statementOptions must not be null");
        this.rowMapper = Objects.requireNonNull(rowMapper, "rowMapper must not be null");
        this.tagOperations = Objects.requireNonNull(tagOperations, "tagOperations must not be null");
    }

    List<OperationChainPublicationStage> findStaged(Connection connection,
                                                    Instant cutoff,
                                                    OperationChainPublicationStageCursor after,
                                                    PageRequest pageRequest)
            throws SQLException {
        String cursorPredicate = after == null ? ""
                : " AND (staged_at > ? OR (staged_at = ? AND stage_id > ?))";
        String orderedStagesSql = "SELECT " + STAGE_COLUMNS
                + " FROM operation_chain_publication_stage WHERE staged_at <= ?" + cursorPredicate
                + " ORDER BY staged_at, stage_id";
        String pagedStagesSql = ExternalRepositorySqlDialect.pagedSql(databaseDialect, orderedStagesSql);
        String sql = "SELECT " + PAGED_STAGE_COLUMNS + ", tag_row.tag AS stage_tag FROM ("
                + pagedStagesSql + ") staged_page "
                + "LEFT JOIN operation_chain_publication_stage_tag tag_row "
                + "ON tag_row.stage_id=staged_page.stage_id "
                + "ORDER BY staged_page.staged_at, staged_page.stage_id, tag_row.tag";
        try (PreparedStatement statement = prepare(connection, sql)) {
            databaseDialect.setInstant(statement, 1, cutoff);
            int pageParameterIndex = 2;
            if (after != null) {
                databaseDialect.setInstant(statement, 2, after.stagedAt());
                databaseDialect.setInstant(statement, 3, after.stagedAt());
                statement.setString(4, after.stageId());
                pageParameterIndex = 5;
            }
            ExternalRepositorySqlDialect.bindPage(databaseDialect, statement, pageParameterIndex, pageRequest);
            List<OperationChainPublicationStage> stages = new ArrayList<>();
            OperationChainPublicationStage currentStage = null;
            List<String> currentTags = new ArrayList<>();
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    OperationChainPublicationStage rowStage = rowMapper.mapStage(resultSet);
                    if (currentStage != null && !currentStage.stageId().equals(rowStage.stageId())) {
                        stages.add(OperationChainObjectRowMapper.copyStageWithTags(currentStage,
                                                                                   List.copyOf(currentTags)));
                        currentTags.clear();
                    }
                    currentStage = rowStage;
                    String tag = resultSet.getString("stage_tag");
                    if (tag != null) {
                        currentTags.add(tag);
                    }
                }
            }
            if (currentStage != null) {
                stages.add(OperationChainObjectRowMapper.copyStageWithTags(currentStage, List.copyOf(currentTags)));
            }
            return List.copyOf(stages);
        }
    }

    void insertStage(Connection connection,
                     String stageId,
                     OperationChainObject object,
                     String storeFingerprint,
                     Instant stagedAt)
            throws SQLException {
        String sql = "INSERT INTO operation_chain_publication_stage(stage_id, al_id, version, publication_mode, "
                + "content_hash, size_bytes, mime_type, created_at, created_by, published_at, store_fingerprint, "
                + "staged_at, stage_revision) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, stageId);
            statement.setString(2, object.alId());
            statement.setString(3, object.version());
            ExternalRepositorySqlDialect.bindExecutionMode(databaseDialect, statement, 4, object.mode());
            statement.setString(5, OperationChainObjectRowMapper.requireContentHash(object.contentHash()));
            statement.setLong(6, object.sizeBytes());
            statement.setString(7, object.mimeType());
            databaseDialect.setInstant(statement, 8, object.createdAt());
            statement.setString(9, object.createdBy());
            databaseDialect.setInstant(statement, 10, object.publishedAt());
            statement.setString(11, storeFingerprint);
            databaseDialect.setInstant(statement, 12, stagedAt);
            statement.setLong(13, 1L);
            int insertedRows = statement.executeUpdate();
            if (insertedRows != 1) {
                throw new SQLException("Expected to insert one publication stage but inserted " + insertedRows);
            }
        }
    }

    void lockForUpdate(Connection connection, String stageId) throws SQLException {
        String sql = "SELECT stage_id FROM operation_chain_publication_stage WHERE stage_id=? FOR UPDATE";
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, stageId);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Publication stage disappeared while it was being locked: " + stageId);
                }
            }
        }
    }

    void renew(Connection connection, String stageId, Instant stagedAt) throws SQLException {
        String sql = "UPDATE operation_chain_publication_stage SET staged_at=?, "
                + "stage_revision=stage_revision+1 WHERE stage_id=?";
        try (PreparedStatement statement = prepare(connection, sql)) {
            databaseDialect.setInstant(statement, 1, stagedAt);
            statement.setString(2, stageId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Publication stage disappeared while it was being renewed: " + stageId);
            }
        }
    }

    Optional<OperationChainPublicationStage> find(Connection connection, String stageId) throws SQLException {
        String sql = "SELECT " + STAGE_COLUMNS + " FROM operation_chain_publication_stage WHERE stage_id=?";
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, stageId);
            OperationChainPublicationStage stage;
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                stage = rowMapper.mapStage(resultSet);
            }
            List<String> tags = tagOperations.findStageTags(connection, stage.stageId());
            return Optional.of(OperationChainObjectRowMapper.copyStageWithTags(stage, tags));
        }
    }

    void delete(Connection connection, String stageId) throws SQLException {
        tagOperations.deleteStageTags(connection, stageId);
        try (PreparedStatement statement = prepare(connection,
                                                   "DELETE FROM operation_chain_publication_stage WHERE stage_id=?")) {
            statement.setString(1, stageId);
            statement.executeUpdate();
        }
    }

    boolean deleteIfRevisionMatches(Connection connection, String stageId, long revision) throws SQLException {
        String sql = "DELETE FROM operation_chain_publication_stage WHERE stage_id=? AND stage_revision=?";
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, stageId);
            statement.setLong(2, revision);
            return statement.executeUpdate() == 1;
        }
    }

    private PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        return statementOptions.prepare(connection, sql);
    }
}

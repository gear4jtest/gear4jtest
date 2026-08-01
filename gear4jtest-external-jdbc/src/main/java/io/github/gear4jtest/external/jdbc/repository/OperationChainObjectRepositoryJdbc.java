package io.github.gear4jtest.external.jdbc.repository;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationConflictException;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationStage;
import io.github.gear4jtest.external.api.repository.OperationChainRepositoryException;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions;
import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;

public final class OperationChainObjectRepositoryJdbc
        implements OperationChainObjectRepository, OperationChainPublicationRepository {
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-fA-F]{64}");
    private static final String OBJECT_COLUMNS = "id, al_id, version, publication_mode, content_hash, size_bytes, "
            + "mime_type, created_at, created_by, published_at";
    private static final String STAGE_COLUMNS = "stage_id, al_id, version, publication_mode, content_hash, "
            + "size_bytes, mime_type, created_at, created_by, published_at, store_fingerprint, staged_at, "
            + "stage_revision";
    private static final String PAGED_STAGE_COLUMNS = "staged_page."
            + STAGE_COLUMNS.replace(", ", ", staged_page.");
    private static final PageRequest FIRST_ROW = PageRequest.first(1);

    private final DataSource ds;
    private final Gear4jDatabaseDialect databaseDialect;
    private final JdbcStatementOptions statementOptions;
    private final JdbcTransactionOperations transactionOperations;

    public static Builder builder() {
        return new Builder();
    }

    private OperationChainObjectRepositoryJdbc(Builder builder) {
        this.ds = Objects.requireNonNull(builder.dataSource, "ds must not be null");
        this.databaseDialect = Objects.requireNonNull(builder.databaseDialect, "databaseDialect must not be null");
        this.statementOptions = Objects.requireNonNull(builder.statementOptions,
                                                       "statementOptions must not be null");
        this.transactionOperations = builder.transactionOperations != null
                ? builder.transactionOperations
                : JdbcTransactionOperations.autonomous(ds);
    }

    public static final class Builder {
        private DataSource dataSource;
        private Gear4jDatabaseDialect databaseDialect;
        private JdbcStatementOptions statementOptions = JdbcStatementOptions.defaults();
        private JdbcTransactionOperations transactionOperations;

        private Builder() {
        }

        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        public Builder databaseDialect(Gear4jDatabaseDialect databaseDialect) {
            this.databaseDialect = databaseDialect;
            return this;
        }

        public Builder jdbcStatementTimeout(Duration jdbcStatementTimeout) {
            this.statementOptions = JdbcStatementOptions.of(jdbcStatementTimeout);
            return this;
        }

        public Builder statementOptions(JdbcStatementOptions statementOptions) {
            this.statementOptions = statementOptions;
            return this;
        }

        /**
         * Configures ownership of publication write transactions. When omitted, Gear4J
         * uses library-owned autonomous transactions.
         */
        public Builder transactionOperations(JdbcTransactionOperations transactionOperations) {
            this.transactionOperations = Objects.requireNonNull(transactionOperations,
                                                                "transactionOperations must not be null");
            return this;
        }

        public OperationChainObjectRepositoryJdbc build() {
            return new OperationChainObjectRepositoryJdbc(this);
        }
    }

    @Override
    public boolean supportsStaging() {
        return true;
    }

    private Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        return databaseDialect.getInstant(rs, column);
    }

    private void setTimestamp(PreparedStatement ps, int index, Instant instant) throws SQLException {
        databaseDialect.setInstant(ps, index, instant);
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

    private OperationChainObject map(ResultSet rs) throws SQLException {
        return new OperationChainObject(rs.getLong("id"), rs.getString("al_id"), rs.getString("version"),
                ExecutionMode.valueOf(rs.getString("publication_mode")),
                requireContentHash(rs.getString("content_hash")), rs.getLong("size_bytes"),
                rs.getString("mime_type"), instantOrNull(rs, "created_at"),
                rs.getString("created_by"), instantOrNull(rs, "published_at"));
    }

    private OperationChainPublicationStage mapStageRow(ResultSet resultSet) throws SQLException {
        OperationChainObject object = new OperationChainObject(null, resultSet.getString("al_id"),
                resultSet.getString("version"), ExecutionMode.valueOf(resultSet.getString("publication_mode")),
                requireContentHash(resultSet.getString("content_hash")), resultSet.getLong("size_bytes"),
                resultSet.getString("mime_type"), instantOrNull(resultSet, "created_at"),
                resultSet.getString("created_by"), instantOrNull(resultSet, "published_at"));
        return new OperationChainPublicationStage(resultSet.getString("stage_id"), object, List.of(),
                resultSet.getString("store_fingerprint"), instantOrNull(resultSet, "staged_at"),
                resultSet.getLong("stage_revision"));
    }

    private OperationChainPublicationStage withStageTags(Connection connection,
                                                         OperationChainPublicationStage stage)
            throws SQLException {
        return copyStageWithTags(stage, findStageTags(connection, stage.stageId()));
    }

    private static OperationChainPublicationStage copyStageWithTags(OperationChainPublicationStage stage,
                                                                    List<String> tags) {
        return new OperationChainPublicationStage(stage.stageId(), stage.object(), tags,
                stage.storeFingerprint(), stage.stagedAt(), stage.revision());
    }

    private static String requireContentHash(String contentHash) {
        if (contentHash == null || !SHA_256_HEX.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("Invalid SHA-256 content hash: " + contentHash);
        }
        return contentHash.toLowerCase(Locale.ROOT);
    }

    @Override
    public long insert(OperationChainObject object) {
        Objects.requireNonNull(object, "object must not be null");
        try {
            return transactionOperations.executeReturning(connection -> insert(connection, object));
        } catch (SQLException exception) {
            throw repositoryFailure("insert operation-chain object " + publicationKey(object), exception);
        }
    }

    @Override
    public void publish(OperationChainObject object, List<String> tags) {
        try {
            OperationChainPublicationStage stage = stage(object, tags);
            commit(stage.stageId());
        } catch (OperationChainPublicationConflictException exception) {
            throw exception;
        } catch (OperationChainRepositoryException exception) {
            throw new OperationChainRepositoryException(
                    "Failed to publish operation-chain object " + publicationKey(object)
                            + " using " + databaseDialect,
                    exception);
        }
    }

    @Override
    public OperationChainPublicationStage stage(OperationChainObject object,
                                                List<String> tags,
                                                String storeFingerprint) {
        Objects.requireNonNull(object, "object must not be null");
        List<String> requiredTags = normalizedTags(tags);
        String requiredStoreFingerprint = requireContentHash(storeFingerprint);
        String stageId = deterministicStageId(object);
        try {
            return transactionOperations.executeReturning(connection -> {
                verifyCommittedPublicationCompatible(connection, object);
                StageInsertResult stageResult = insertStageIdempotently(connection, stageId, object,
                                                                        requiredStoreFingerprint);
                insertStageTags(connection, stageId, requiredTags);
                if (!stageResult.inserted()) {
                    renewStage(connection, stageId, Instant.now());
                }
                return findStage(connection, stageId).orElseThrow();
            });
        } catch (OperationChainRepositoryException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw repositoryFailure("stage operation-chain object " + publicationKey(object), exception);
        }
    }

    @Override
    public void commit(String stageId) {
        String requiredStageId = requireStageId(stageId);
        try {
            transactionOperations.execute(connection -> {
                OperationChainPublicationStage stage = findStage(connection, requiredStageId).orElse(null);
                if (stage != null) {
                    insertIdempotently(connection, stage.object());
                    insertTags(connection, stage.object().alId(), stage.tags());
                    deleteStage(connection, requiredStageId);
                }
            });
        } catch (OperationChainRepositoryException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw repositoryFailure("commit operation-chain stage " + requiredStageId, exception);
        }
    }

    @Override
    public void abort(String stageId) {
        String requiredStageId = requireStageId(stageId);
        try {
            transactionOperations.execute(connection -> {
                deleteStage(connection, requiredStageId);
            });
        } catch (OperationChainRepositoryException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw repositoryFailure("abort operation-chain stage " + requiredStageId, exception);
        }
    }

    @Override
    public boolean abortIfUnchanged(OperationChainPublicationStage expectedStage) {
        OperationChainPublicationStage requiredStage = Objects.requireNonNull(expectedStage,
                                                                              "expectedStage must not be null");
        try {
            return transactionOperations.executeReturning(connection -> deleteStageIfRevisionMatches(
                                                                                                     connection,
                                                                                                     requiredStage
                                                                                                             .stageId(),
                                                                                                     requiredStage
                                                                                                             .revision()));
        } catch (OperationChainRepositoryException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw repositoryFailure("conditionally abort operation-chain stage " + requiredStage.stageId(),
                                    exception);
        }
    }

    @Override
    public List<OperationChainPublicationStage> findStagedBefore(Instant cutoff, PageRequest pageRequest) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        String orderedStagesSql = "SELECT " + STAGE_COLUMNS
                + " FROM operation_chain_publication_stage WHERE staged_at <= ? ORDER BY staged_at, stage_id";
        String pagedStagesSql = ExternalRepositorySqlDialect.pagedSql(databaseDialect, orderedStagesSql);
        String sql = "SELECT " + PAGED_STAGE_COLUMNS + ", tag_row.tag AS stage_tag FROM ("
                + pagedStagesSql + ") staged_page "
                + "LEFT JOIN operation_chain_publication_stage_tag tag_row "
                + "ON tag_row.stage_id=staged_page.stage_id "
                + "ORDER BY staged_page.staged_at, staged_page.stage_id, tag_row.tag";
        try (Connection connection = ds.getConnection(); PreparedStatement statement = prepare(connection, sql)) {
            setTimestamp(statement, 1, cutoff);
            ExternalRepositorySqlDialect.bindPage(databaseDialect, statement, 2, pageRequest);
            List<OperationChainPublicationStage> stages = new ArrayList<>();
            OperationChainPublicationStage currentStage = null;
            List<String> currentTags = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    OperationChainPublicationStage rowStage = mapStageRow(resultSet);
                    if (currentStage != null && !currentStage.stageId().equals(rowStage.stageId())) {
                        stages.add(copyStageWithTags(currentStage, List.copyOf(currentTags)));
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
                stages.add(copyStageWithTags(currentStage, List.copyOf(currentTags)));
            }
            return List.copyOf(stages);
        } catch (SQLException exception) {
            throw repositoryFailure("find staged operation-chain publications", exception);
        }
    }

    private StageInsertResult insertStageIdempotently(Connection connection,
                                                      String stageId,
                                                      OperationChainObject object,
                                                      String storeFingerprint)
            throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            insertStage(connection, stageId, object, storeFingerprint, Instant.now());
            return new StageInsertResult(true);
        } catch (SQLException exception) {
            if (!ExternalRepositorySqlDialect.isUniqueViolation(databaseDialect, exception)) {
                throw exception;
            }
            connection.rollback(savepoint);
            OperationChainPublicationStage existing = findStage(connection, stageId)
                    .orElseThrow(() -> repositoryFailure("resolve concurrent stage " + publicationKey(object),
                                                         exception));
            if (!existing.object().contentIdentity().equals(object.contentIdentity())
                    || !Objects.equals(existing.storeFingerprint(), storeFingerprint)) {
                throw conflict(object, exception);
            }
            return new StageInsertResult(false);
        }
    }

    private void insertStage(Connection connection,
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
            statement.setString(5, requireContentHash(object.contentHash()));
            statement.setLong(6, object.sizeBytes());
            statement.setString(7, object.mimeType());
            setTimestamp(statement, 8, object.createdAt());
            statement.setString(9, object.createdBy());
            setTimestamp(statement, 10, object.publishedAt());
            statement.setString(11, storeFingerprint);
            setTimestamp(statement, 12, stagedAt);
            statement.setLong(13, 1L);
            int insertedRows = statement.executeUpdate();
            if (insertedRows != 1) {
                throw new SQLException("Expected to insert one publication stage but inserted " + insertedRows);
            }
        }
    }

    private void renewStage(Connection connection, String stageId, Instant stagedAt) throws SQLException {
        String sql = "UPDATE operation_chain_publication_stage SET staged_at=?, "
                + "stage_revision=stage_revision+1 WHERE stage_id=?";
        try (PreparedStatement statement = prepare(connection, sql)) {
            setTimestamp(statement, 1, stagedAt);
            statement.setString(2, stageId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Publication stage disappeared while it was being renewed: " + stageId);
            }
        }
    }

    private void verifyCommittedPublicationCompatible(Connection connection, OperationChainObject candidate)
            throws SQLException {
        Optional<OperationChainObject> committed = find(connection, candidate.alId(), candidate.version(),
                                                        candidate.mode());
        if (committed.isPresent()
                && !committed.get().contentIdentity().equals(candidate.contentIdentity())) {
            throw conflict(candidate, null);
        }
    }

    private Optional<OperationChainPublicationStage> findStage(Connection connection, String stageId)
            throws SQLException {
        String sql = "SELECT " + STAGE_COLUMNS + " FROM operation_chain_publication_stage WHERE stage_id=?";
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, stageId);
            OperationChainPublicationStage stage;
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                stage = mapStageRow(resultSet);
            }
            return Optional.of(withStageTags(connection, stage));
        }
    }

    private List<String> findStageTags(Connection connection, String stageId) throws SQLException {
        String sql = "SELECT tag FROM operation_chain_publication_stage_tag WHERE stage_id=? ORDER BY tag";
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, stageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> tags = new ArrayList<>();
                while (resultSet.next()) {
                    tags.add(resultSet.getString(1));
                }
                return List.copyOf(tags);
            }
        }
    }

    private void insertStageTags(Connection connection, String stageId, List<String> tags) throws SQLException {
        for (String tag : tags) {
            Savepoint savepoint = connection.setSavepoint();
            try (PreparedStatement statement = prepare(connection,
                                                       "INSERT INTO operation_chain_publication_stage_tag(stage_id, "
                                                               + "tag) VALUES (?,?)")) {
                statement.setString(1, stageId);
                statement.setString(2, tag);
                statement.executeUpdate();
            } catch (SQLException exception) {
                if (!ExternalRepositorySqlDialect.isUniqueViolation(databaseDialect, exception)) {
                    throw exception;
                }
                connection.rollback(savepoint);
            }
        }
    }

    private void deleteStage(Connection connection, String stageId) throws SQLException {
        try (PreparedStatement deleteTags = prepare(connection,
                                                    "DELETE FROM operation_chain_publication_stage_tag WHERE "
                                                            + "stage_id=?")) {
            deleteTags.setString(1, stageId);
            deleteTags.executeUpdate();
        }
        try (PreparedStatement deleteStage = prepare(
                                                     connection,
                                                     "DELETE FROM operation_chain_publication_stage WHERE stage_id=?")) {
            deleteStage.setString(1, stageId);
            deleteStage.executeUpdate();
        }
    }

    private boolean deleteStageIfRevisionMatches(Connection connection, String stageId, long revision)
            throws SQLException {
        String sql = "DELETE FROM operation_chain_publication_stage WHERE stage_id=? AND stage_revision=?";
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, stageId);
            statement.setLong(2, revision);
            return statement.executeUpdate() == 1;
        }
    }

    private long insert(Connection connection, OperationChainObject object) throws SQLException {
        String sql = "INSERT INTO operation_chain_object(al_id, version, publication_mode, content_hash, size_bytes, "
                + "mime_type, created_at, created_by, published_at) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = prepareGeneratedKeyInsert(connection, sql)) {
            statement.setString(1, object.alId());
            statement.setString(2, object.version());
            ExternalRepositorySqlDialect.bindExecutionMode(databaseDialect, statement, 3, object.mode());
            statement.setString(4, requireContentHash(object.contentHash()));
            statement.setLong(5, object.sizeBytes());
            statement.setString(6, object.mimeType());
            setTimestamp(statement, 7, object.createdAt());
            statement.setString(8, object.createdBy());
            setTimestamp(statement, 9, object.publishedAt());
            int insertedRows = statement.executeUpdate();
            if (insertedRows != 1) {
                throw new SQLException("Expected to insert one operation-chain object but inserted " + insertedRows);
            }
            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                return generatedKeys.next() ? generatedKeys.getLong(1) : -1L;
            }
        }
    }

    private void insertIdempotently(Connection connection, OperationChainObject object) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            insert(connection, object);
        } catch (SQLException exception) {
            if (!ExternalRepositorySqlDialect.isUniqueViolation(databaseDialect, exception)) {
                throw exception;
            }
            connection.rollback(savepoint);
            OperationChainObject existing = find(connection, object.alId(), object.version(), object.mode())
                    .orElseThrow(() -> repositoryFailure(
                                                         "resolve concurrent publication " + publicationKey(object),
                                                         exception));
            if (!existing.contentIdentity().equals(object.contentIdentity())) {
                throw conflict(object, exception);
            }
        }
    }

    private void insertTags(Connection connection, String assemblyLineId, List<String> tags) throws SQLException {
        if (tags.isEmpty()) {
            return;
        }
        String sql = ExternalRepositorySqlDialect.insertTagIfAbsentSql(databaseDialect);
        try (PreparedStatement statement = prepare(connection, sql)) {
            for (String tag : tags) {
                statement.setString(1, assemblyLineId);
                statement.setString(2, tag);
                statement.executeUpdate();
            }
        }
    }

    @Override
    public Optional<OperationChainObject> find(String alId, String version, ExecutionMode mode) {
        try (var connection = ds.getConnection()) {
            return find(connection, alId, version, mode);
        } catch (SQLException exception) {
            throw repositoryFailure("find operation-chain object " + alId + ":" + version + ":" + mode,
                                    exception);
        }
    }

    private Optional<OperationChainObject> find(Connection connection,
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
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    private static String publicationKey(OperationChainObject object) {
        return object.alId() + ":" + object.version() + ":" + object.mode();
    }

    private static String deterministicStageId(OperationChainObject object) {
        return UUID.nameUUIDFromBytes(publicationKey(object).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static List<String> normalizedTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String tag : tags) {
            if (tag == null || tag.isBlank()) {
                throw new IllegalArgumentException("tag must not be blank");
            }
            if (tag.length() > 100) {
                throw new IllegalArgumentException("tag must not exceed 100 characters");
            }
            normalized.add(tag);
        }
        return List.copyOf(normalized);
    }

    private static String requireStageId(String stageId) {
        if (stageId == null || stageId.isBlank()) {
            throw new IllegalArgumentException("stageId must not be blank");
        }
        return stageId;
    }

    private static OperationChainPublicationConflictException conflict(OperationChainObject object, Throwable cause) {
        String message = "Publication " + publicationKey(object)
                + " already exists with different content or metadata";
        return cause == null ? new OperationChainPublicationConflictException(message)
                : new OperationChainPublicationConflictException(message, cause);
    }

    private OperationChainRepositoryException repositoryFailure(String operation, SQLException cause) {
        return new OperationChainRepositoryException("Failed to " + operation + " using " + databaseDialect, cause);
    }

    private record StageInsertResult(boolean inserted) {}

    @Override
    public Optional<OperationChainObject> findLatestRun(String alId) {
        String orderedSql = "SELECT " + OBJECT_COLUMNS
                + " FROM operation_chain_object WHERE al_id=? AND publication_mode='RUN' "
                + "ORDER BY published_at DESC, id DESC";
        String sql = ExternalRepositorySqlDialect.pagedSql(databaseDialect, orderedSql);
        try (var connection = ds.getConnection(); var statement = prepare(connection, sql)) {
            statement.setString(1, alId);
            ExternalRepositorySqlDialect.bindPage(databaseDialect, statement, 2, FIRST_ROW);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw repositoryFailure("find latest RUN operation-chain object for " + alId, exception);
        }
    }

    @Override
    public boolean exists(String alId, String version, ExecutionMode mode) {
        String sql = "SELECT 1 FROM operation_chain_object WHERE al_id=? AND version=? AND publication_mode=?";
        try (var connection = ds.getConnection(); var statement = prepare(connection, sql)) {
            statement.setString(1, alId);
            statement.setString(2, version);
            ExternalRepositorySqlDialect.bindExecutionMode(databaseDialect, statement, 3, mode);
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw repositoryFailure("check operation-chain object " + alId + ":" + version + ":" + mode,
                                    exception);
        }
    }

    @Override
    public List<OperationChainObject> findAll(String alId, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        String orderedSql = "SELECT " + OBJECT_COLUMNS
                + " FROM operation_chain_object WHERE al_id=? ORDER BY published_at DESC, id DESC";
        String sql = ExternalRepositorySqlDialect.pagedSql(databaseDialect, orderedSql);
        try (var connection = ds.getConnection(); var statement = prepare(connection, sql)) {
            statement.setString(1, alId);
            ExternalRepositorySqlDialect.bindPage(databaseDialect, statement, 2, pageRequest);
            try (var resultSet = statement.executeQuery()) {
                List<OperationChainObject> list = new ArrayList<>();
                while (resultSet.next()) {
                    list.add(map(resultSet));
                }
                return list;
            }
        } catch (SQLException exception) {
            throw repositoryFailure("find operation-chain objects for " + alId, exception);
        }
    }
}

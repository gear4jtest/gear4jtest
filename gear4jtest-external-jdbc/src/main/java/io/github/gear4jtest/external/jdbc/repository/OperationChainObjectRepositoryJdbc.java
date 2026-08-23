package io.github.gear4jtest.external.jdbc.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.identity.OperationChainIdentityCodec;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainObjectCursor;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationConflictException;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationStage;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationStageCursor;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationTags;
import io.github.gear4jtest.external.api.repository.OperationChainRepositoryException;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions;
import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;

/**
 * Public JDBC facade for object lookup and atomic publication.
 *
 * <p>
 * Transaction orchestration remains here while object, stage, tag and
 * row-mapping SQL are isolated in package-private collaborators.
 * </p>
 */
public final class OperationChainObjectRepositoryJdbc
        implements OperationChainObjectRepository, OperationChainPublicationRepository {
    private final DataSource ds;
    private final Gear4jDatabaseDialect databaseDialect;
    private final JdbcTransactionOperations transactionOperations;
    private final OperationChainObjectJdbcOperations objectOperations;
    private final OperationChainPublicationStageJdbcOperations stageOperations;
    private final OperationChainTagJdbcOperations tagOperations;

    public static Builder builder() {
        return new Builder();
    }

    private OperationChainObjectRepositoryJdbc(Builder builder) {
        this.ds = Objects.requireNonNull(builder.dataSource, "ds must not be null");
        this.databaseDialect = Objects.requireNonNull(builder.databaseDialect, "databaseDialect must not be null");
        JdbcStatementOptions statementOptions = Objects.requireNonNull(builder.statementOptions,
                                                                       "statementOptions must not be null");
        this.transactionOperations = builder.transactionOperations != null
                ? builder.transactionOperations
                : JdbcTransactionOperations.autonomous(ds);
        OperationChainObjectRowMapper rowMapper = new OperationChainObjectRowMapper(databaseDialect);
        this.tagOperations = new OperationChainTagJdbcOperations(databaseDialect, statementOptions);
        this.objectOperations = new OperationChainObjectJdbcOperations(databaseDialect, statementOptions, rowMapper);
        this.stageOperations = new OperationChainPublicationStageJdbcOperations(databaseDialect, statementOptions,
                rowMapper, tagOperations);
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

    @Override
    public long insert(OperationChainObject object) {
        Objects.requireNonNull(object, "object must not be null");
        try {
            return transactionOperations.executeReturning(connection -> objectOperations.insert(connection, object));
        } catch (SQLException exception) {
            throw repositoryFailure("insert operation-chain object " + publicationDescription(object), exception);
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
                    "Failed to publish operation-chain object " + publicationDescription(object)
                            + " using " + databaseDialect,
                    exception);
        }
    }

    @Override
    public OperationChainPublicationStage stage(OperationChainObject object,
                                                List<String> tags,
                                                String storeFingerprint) {
        Objects.requireNonNull(object, "object must not be null");
        List<String> requiredTags = OperationChainPublicationTags.normalize(tags);
        String requiredStoreFingerprint = OperationChainObjectRowMapper.requireContentHash(storeFingerprint);
        String stageId = OperationChainIdentityCodec.publicationStageId(object);
        try {
            return transactionOperations.executeReturning(connection -> {
                verifyCommittedPublicationCompatible(connection, object);
                StageInsertResult stageResult = insertStageIdempotently(connection, stageId, object,
                                                                        requiredStoreFingerprint);
                if (!stageResult.inserted()) {
                    stageOperations.lockForUpdate(connection, stageId);
                    OperationChainPublicationTags.merge(tagOperations.findStageTags(connection, stageId), requiredTags);
                }
                tagOperations.insertStageTags(connection, stageId, requiredTags);
                if (!stageResult.inserted()) {
                    stageOperations.renew(connection, stageId, Instant.now());
                }
                return stageOperations.find(connection, stageId).orElseThrow();
            });
        } catch (OperationChainRepositoryException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw repositoryFailure("stage operation-chain object " + publicationDescription(object), exception);
        }
    }

    @Override
    public void commit(String stageId) {
        String requiredStageId = requireStageId(stageId);
        try {
            transactionOperations.execute(connection -> {
                OperationChainPublicationStage stage = stageOperations.find(connection, requiredStageId).orElse(null);
                if (stage != null) {
                    insertIdempotently(connection, stage.object());
                    tagOperations.insertObjectTags(connection, stage.object().alId(), stage.tags());
                    stageOperations.delete(connection, requiredStageId);
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
            transactionOperations.execute(connection -> stageOperations.delete(connection, requiredStageId));
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
            return transactionOperations.executeReturning(connection -> stageOperations.deleteIfRevisionMatches(
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
        return findStaged(cutoff, null, pageRequest);
    }

    @Override
    public List<OperationChainPublicationStage> findStagedAfter(Instant cutoff,
                                                                OperationChainPublicationStageCursor after,
                                                                int limit) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        return findStaged(cutoff, after, PageRequest.first(limit));
    }

    private List<OperationChainPublicationStage> findStaged(Instant cutoff,
                                                            OperationChainPublicationStageCursor after,
                                                            PageRequest pageRequest) {
        try (Connection connection = ds.getConnection()) {
            return stageOperations.findStaged(connection, cutoff, after, pageRequest);
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
            stageOperations.insertStage(connection, stageId, object, storeFingerprint, Instant.now());
            return new StageInsertResult(true);
        } catch (SQLException exception) {
            if (!ExternalRepositorySqlDialect.isUniqueViolation(databaseDialect, exception)) {
                throw exception;
            }
            connection.rollback(savepoint);
            OperationChainPublicationStage existing = stageOperations.find(connection, stageId)
                    .orElseThrow(() -> repositoryFailure("resolve concurrent stage " + publicationDescription(object),
                                                         exception));
            if (!existing.object().contentIdentity().equals(object.contentIdentity())
                    || !Objects.equals(existing.storeFingerprint(), storeFingerprint)) {
                throw conflict(object, exception);
            }
            return new StageInsertResult(false);
        }
    }

    private void verifyCommittedPublicationCompatible(Connection connection, OperationChainObject candidate)
            throws SQLException {
        Optional<OperationChainObject> committed = objectOperations.find(connection, candidate.alId(),
                                                                         candidate.version(), candidate.mode());
        if (committed.isPresent() && !committed.get().contentIdentity().equals(candidate.contentIdentity())) {
            throw conflict(candidate, null);
        }
    }

    private void insertIdempotently(Connection connection, OperationChainObject object) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            objectOperations.insert(connection, object);
        } catch (SQLException exception) {
            if (!ExternalRepositorySqlDialect.isUniqueViolation(databaseDialect, exception)) {
                throw exception;
            }
            connection.rollback(savepoint);
            OperationChainObject existing = objectOperations.find(connection, object.alId(), object.version(),
                                                                  object.mode())
                    .orElseThrow(() -> repositoryFailure(
                                                         "resolve concurrent publication "
                                                                 + publicationDescription(object),
                                                         exception));
            if (!existing.contentIdentity().equals(object.contentIdentity())) {
                throw conflict(object, exception);
            }
        }
    }

    @Override
    public Optional<OperationChainObject> find(String alId, String version, ExecutionMode mode) {
        try (Connection connection = ds.getConnection()) {
            return objectOperations.find(connection, alId, version, mode);
        } catch (SQLException exception) {
            throw repositoryFailure("find operation-chain object " + alId + ":" + version + ":" + mode,
                                    exception);
        }
    }

    @Override
    public Optional<OperationChainObject> findLatestRun(String alId) {
        try (Connection connection = ds.getConnection()) {
            return objectOperations.findLatestRun(connection, alId);
        } catch (SQLException exception) {
            throw repositoryFailure("find latest RUN operation-chain object for " + alId, exception);
        }
    }

    @Override
    public boolean exists(String alId, String version, ExecutionMode mode) {
        try (Connection connection = ds.getConnection()) {
            return objectOperations.exists(connection, alId, version, mode);
        } catch (SQLException exception) {
            throw repositoryFailure("check operation-chain object " + alId + ":" + version + ":" + mode,
                                    exception);
        }
    }

    @Override
    public List<OperationChainObject> findAll(String alId, PageRequest pageRequest) {
        Objects.requireNonNull(pageRequest, "pageRequest must not be null");
        try (Connection connection = ds.getConnection()) {
            return objectOperations.findAll(connection, alId, pageRequest);
        } catch (SQLException exception) {
            throw repositoryFailure("find operation-chain objects for " + alId, exception);
        }
    }

    @Override
    public List<OperationChainObject> findAllAfter(String alId, OperationChainObjectCursor after, int limit) {
        try (Connection connection = ds.getConnection()) {
            return objectOperations.findAllAfter(connection, alId, after, limit);
        } catch (SQLException exception) {
            throw repositoryFailure("find operation-chain objects after cursor for " + alId, exception);
        }
    }

    private static String publicationDescription(OperationChainObject object) {
        return object.alId() + ":" + object.version() + ":" + object.mode();
    }

    private static String requireStageId(String stageId) {
        if (stageId == null || stageId.isBlank()) {
            throw new IllegalArgumentException("stageId must not be blank");
        }
        return stageId;
    }

    private static OperationChainPublicationConflictException conflict(OperationChainObject object, Throwable cause) {
        String message = "Publication " + publicationDescription(object)
                + " already exists with different content or metadata";
        return cause == null ? new OperationChainPublicationConflictException(message)
                : new OperationChainPublicationConflictException(message, cause);
    }

    private OperationChainRepositoryException repositoryFailure(String operation, SQLException cause) {
        return new OperationChainRepositoryException("Failed to " + operation + " using " + databaseDialect, cause);
    }

    private record StageInsertResult(boolean inserted) {}
}

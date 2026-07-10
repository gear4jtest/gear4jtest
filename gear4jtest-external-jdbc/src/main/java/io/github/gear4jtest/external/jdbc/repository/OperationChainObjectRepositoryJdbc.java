package io.github.gear4jtest.external.jdbc.repository;

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
import java.util.regex.Pattern;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationConflictException;
import io.github.gear4jtest.external.api.repository.OperationChainPublicationRepository;
import io.github.gear4jtest.external.api.repository.OperationChainRepositoryException;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcStatementOptions;

public final class OperationChainObjectRepositoryJdbc
        implements OperationChainObjectRepository, OperationChainPublicationRepository {
    private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    private final DataSource ds;
    private final Gear4jDatabaseDialect databaseDialect;
    private final JdbcStatementOptions statementOptions;

    public static Builder builder() {
        return new Builder();
    }

    private OperationChainObjectRepositoryJdbc(Builder builder) {
        this.ds = Objects.requireNonNull(builder.dataSource, "ds must not be null");
        this.databaseDialect = Objects.requireNonNull(builder.databaseDialect, "databaseDialect must not be null");
        this.statementOptions = Objects.requireNonNull(builder.statementOptions,
                                                       "statementOptions must not be null");
    }

    public static final class Builder {
        private DataSource dataSource;
        private Gear4jDatabaseDialect databaseDialect;
        private JdbcStatementOptions statementOptions = JdbcStatementOptions.defaults();

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

        public OperationChainObjectRepositoryJdbc build() {
            return new OperationChainObjectRepositoryJdbc(this);
        }
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
                ExecutionMode.valueOf(rs.getString("mode")), requireContentHash(rs.getString("content_hash")),
                rs.getLong("size_bytes"), rs.getString("mime_type"), instantOrNull(rs, "created_at"),
                rs.getString("created_by"), instantOrNull(rs, "published_at"));
    }

    private static String requireContentHash(String contentHash) {
        if (contentHash == null || !SHA_256_HEX.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("Invalid SHA-256 content hash: " + contentHash);
        }
        return contentHash.toLowerCase(Locale.ROOT);
    }

    @Override
    public long insert(OperationChainObject o) {
        Objects.requireNonNull(o, "object must not be null");
        try (var c = ds.getConnection()) {
            return insert(c, o);
        } catch (SQLException e) {
            throw repositoryFailure("insert operation-chain object " + publicationKey(o), e);
        }
    }

    @Override
    public void publish(OperationChainObject object, List<String> tags) {
        Objects.requireNonNull(object, "object must not be null");
        List<String> requiredTags = List.copyOf(Objects.requireNonNull(tags, "tags must not be null"));
        try (Connection connection = ds.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            Throwable failure = null;
            try {
                insertIdempotently(connection, object);
                insertTags(connection, object.alId(), requiredTags);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                failure = exception;
                rollback(connection, exception);
                throw exception;
            } finally {
                restoreAutoCommit(connection, previousAutoCommit, failure);
            }
        } catch (OperationChainRepositoryException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw repositoryFailure("publish operation-chain object " + publicationKey(object), exception);
        }
    }

    private long insert(Connection connection, OperationChainObject object) throws SQLException {
        String sql = "INSERT INTO operation_chain_object(al_id, version, mode, content_hash, size_bytes, "
                + "mime_type, created_at, created_by, published_at) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement statement = prepareGeneratedKeyInsert(connection, sql)) {
            statement.setString(1, object.alId());
            statement.setString(2, object.version());
            statement.setString(3, object.mode().name());
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
            if (!samePublishedContent(existing, object)) {
                throw new OperationChainPublicationConflictException("Publication " + publicationKey(object)
                        + " already exists with different content or metadata", exception);
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
        try (var c = ds.getConnection()) {
            return find(c, alId, version, mode);
        } catch (SQLException e) {
            throw repositoryFailure("find operation-chain object " + alId + ":" + version + ":" + mode, e);
        }
    }

    private Optional<OperationChainObject> find(Connection connection,
                                                String assemblyLineId,
                                                String version,
                                                ExecutionMode mode)
            throws SQLException {
        String sql = "SELECT id, al_id, version, mode, content_hash, size_bytes, mime_type, created_at, created_by, published_at "
                + "FROM operation_chain_object WHERE al_id=? AND version=? AND mode=?";
        try (PreparedStatement statement = prepare(connection, sql)) {
            statement.setString(1, assemblyLineId);
            statement.setString(2, version);
            statement.setString(3, mode.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    private static boolean samePublishedContent(OperationChainObject existing, OperationChainObject candidate) {
        return Objects.equals(existing.contentHash(), candidate.contentHash())
                && existing.sizeBytes() == candidate.sizeBytes()
                && Objects.equals(existing.mimeType(), candidate.mimeType());
    }

    private static String publicationKey(OperationChainObject object) {
        return object.alId() + ":" + object.version() + ":" + object.mode();
    }

    private OperationChainRepositoryException repositoryFailure(String operation, SQLException cause) {
        return new OperationChainRepositoryException("Failed to " + operation + " using " + databaseDialect, cause);
    }

    private static void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreAutoCommit(Connection connection, boolean autoCommit, Throwable failure)
            throws SQLException {
        try {
            connection.setAutoCommit(autoCommit);
        } catch (SQLException restoreFailure) {
            if (failure == null) {
                throw restoreFailure;
            }
            failure.addSuppressed(restoreFailure);
        }
    }

    @Override
    public Optional<OperationChainObject> findLatestRun(String alId) {
        String sql = "SELECT id, al_id, version, mode, content_hash, size_bytes, mime_type, created_at, created_by, published_at "
                + "FROM operation_chain_object WHERE al_id=? AND mode='RUN' ORDER BY published_at DESC, id DESC";
        try (var c = ds.getConnection(); var ps = prepare(c, sql)) {
            ps.setString(1, alId);
            ps.setMaxRows(1);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw repositoryFailure("find latest RUN operation-chain object for " + alId, e);
        }
    }

    @Override
    public boolean exists(String alId, String version, ExecutionMode mode) {
        String sql = "SELECT 1 FROM operation_chain_object WHERE al_id=? AND version=? AND mode=?";
        try (var c = ds.getConnection(); var ps = prepare(c, sql)) {
            ps.setString(1, alId);
            ps.setString(2, version);
            ps.setString(3, mode.name());
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw repositoryFailure("check operation-chain object " + alId + ":" + version + ":" + mode, e);
        }
    }

    @Override
    public List<OperationChainObject> findAll(String alId) {
        return findAll(alId, null);
    }

    @Override
    public List<OperationChainObject> findAll(String alId, PageRequest pageRequest) {
        String orderedSql = "SELECT id, al_id, version, mode, content_hash, size_bytes, mime_type, created_at, created_by, published_at "
                + "FROM operation_chain_object WHERE al_id=? ORDER BY published_at DESC, id DESC";
        String sql = pageRequest == null ? orderedSql
                : ExternalRepositorySqlDialect.pagedSql(databaseDialect, orderedSql);
        try (var c = ds.getConnection(); var ps = prepare(c, sql)) {
            ps.setString(1, alId);
            if (pageRequest != null) {
                ExternalRepositorySqlDialect.bindPage(databaseDialect, ps, 2, pageRequest);
            }
            try (var rs = ps.executeQuery()) {
                List<OperationChainObject> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw repositoryFailure("find operation-chain objects for " + alId, e);
        }
    }
}

package io.github.gear4jtest.external.api.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.core.persistence.JdbcStatementOptions;
import io.github.gear4jtest.core.persistence.PageRequest;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.model.OperationChainObject;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;

public final class OperationChainObjectRepositoryJdbc implements OperationChainObjectRepository {
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
        if (databaseDialect == Gear4jDatabaseDialect.POSTGRESQL) {
            OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
            return value != null ? value.toInstant() : null;
        }
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void setTimestamp(PreparedStatement ps, int index, Instant instant) throws SQLException {
        if (instant == null) {
            ps.setNull(index, databaseDialect == Gear4jDatabaseDialect.POSTGRESQL
                    ? Types.TIMESTAMP_WITH_TIMEZONE
                    : Types.TIMESTAMP);
        } else if (databaseDialect == Gear4jDatabaseDialect.POSTGRESQL) {
            ps.setObject(index, instant.atOffset(ZoneOffset.UTC));
        } else {
            ps.setTimestamp(index, Timestamp.from(instant));
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
        String sql = "INSERT INTO operation_chain_object(al_id, version, mode, content_hash, size_bytes, "
                + "mime_type, created_at, created_by, published_at) VALUES (?,?,?,?,?,?,?,?,?)";
        try (var c = ds.getConnection();
                var ps = prepareGeneratedKeyInsert(c, sql)) {
            ps.setString(1, o.alId());
            ps.setString(2, o.version());
            ps.setString(3, o.mode().name());
            ps.setString(4, requireContentHash(o.contentHash()));
            ps.setLong(5, o.sizeBytes());
            ps.setString(6, o.mimeType());
            setTimestamp(ps, 7, o.createdAt());
            ps.setString(8, o.createdBy());
            setTimestamp(ps, 9, o.publishedAt());
            ps.executeUpdate();
            try (var rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<OperationChainObject> find(String alId, String version, ExecutionMode mode) {
        String sql = "SELECT id, al_id, version, mode, content_hash, size_bytes, mime_type, created_at, created_by, published_at "
                + "FROM operation_chain_object WHERE al_id=? AND version=? AND mode=?";
        try (var c = ds.getConnection(); var ps = prepare(c, sql)) {
            ps.setString(1, alId);
            ps.setString(2, version);
            ps.setString(3, mode.name());
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
        }
    }
}

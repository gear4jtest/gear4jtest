package io.github.gear4jtest.external.jdbc.artifact;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;

import io.github.gear4jtest.external.api.artifact.ArtifactHashes;
import io.github.gear4jtest.external.jdbc.repository.ExternalJdbcSchemaMigrator;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
class DatabaseArtifactStoreIntegrityIT {
    @Test
    void fullRead_shouldRejectSameSizeDatabaseCorruption() throws Exception {
        // Given
        Fixture fixture = fixture();
        byte[] expected = "expected".getBytes(StandardCharsets.UTF_8);
        byte[] corrupt = "corrupt!".getBytes(StandardCharsets.UTF_8);
        String hash = fixture.store().put(expected);
        corrupt(fixture.dataSource(), hash, corrupt);

        // When / Then
        assertThatThrownBy(() -> {
            try (var input = fixture.store().get(hash).orElseThrow().openStreamChecked()) {
                input.readAllBytes();
            }
        }).isInstanceOf(IOException.class)
                .hasMessageContaining("content hash mismatch")
                .hasMessageContaining(hash);
    }

    @Test
    void duplicateWrite_shouldRejectSameSizeCorruptExistingContent() throws Exception {
        // Given
        Fixture fixture = fixture();
        byte[] expected = "expected".getBytes(StandardCharsets.UTF_8);
        byte[] corrupt = "corrupt!".getBytes(StandardCharsets.UTF_8);
        String hash = fixture.store().put(expected);
        corrupt(fixture.dataSource(), hash, corrupt);

        // When / Then
        assertThatThrownBy(() -> fixture.store().put(expected))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Failed to persist database artifact")
                .hasRootCauseMessage("Existing artifact content hash mismatch for " + hash + ": expected "
                        + hash + " but found " + ArtifactHashes.sha256Hex(corrupt));
    }

    private static Fixture fixture() throws Exception {
        DataSource dataSource = new DriverManagerBackedDataSource("jdbc:h2:mem:artifact_integrity_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1");
        ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(dataSource);
        DatabaseArtifactStore store = DatabaseArtifactStore.builder()
                .dataSource(dataSource)
                .databaseDialect(Gear4jDatabaseDialect.H2)
                .build();
        return new Fixture(dataSource, store);
    }

    private static void corrupt(DataSource dataSource, String hash, byte[] content) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                                                            "UPDATE artifact_store SET content=? WHERE hash_hex=?")) {
            statement.setBytes(1, content);
            statement.setString(2, hash);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Artifact corruption fixture did not update exactly one row");
            }
        }
    }

    private record Fixture(DataSource dataSource, DatabaseArtifactStore store) {}

    private record DriverManagerBackedDataSource(String url) implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public PrintWriter getLogWriter() {
            return DriverManager.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            DriverManager.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) {
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() {
            return DriverManager.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}

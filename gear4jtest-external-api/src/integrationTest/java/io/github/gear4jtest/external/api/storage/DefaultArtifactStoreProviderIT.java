package io.github.gear4jtest.external.api.storage;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.repository.jdbc.ExternalJdbcSchemaMigrator;
import io.github.gear4jtest.external.api.spi.ArtifactStorePlugin;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class DefaultArtifactStoreProviderIT {
    @TempDir
    Path tempDir;

    @Test
    void provider_shouldResolveMemoryFilesystemAndDatabaseArtifactStoresThroughSpi() throws Exception {
        // Given
        DataSource dataSource = new DriverManagerBackedDataSource("jdbc:h2:mem:gear4j_external_artifacts_"
                + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1", "sa", "");
        ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.H2).migrate(dataSource);
        ArtifactStorePlugin.Context context = key -> "datasource.default".equals(key) ? dataSource : null;
        DefaultArtifactStoreProvider provider = new DefaultArtifactStoreProvider(
                Thread.currentThread().getContextClassLoader(), context, Runnable::run);

        // When / Then
        assertRoundTrip(provider.forConfig(config(StoreType.MEMORY, Map.of())), "memory-content");
        assertRoundTrip(provider.forConfig(config(StoreType.FILESYSTEM, Map.of("root", tempDir.toString()))),
                        "filesystem-content");
        assertRoundTrip(provider.forConfig(config(StoreType.DATABASE,
                                                  Map.of("dialect", Gear4jDatabaseDialect.H2.name()))),
                        "database-content");
    }

    private static OperationChainConfig config(StoreType storeType, Map<String, String> props) {
        return new OperationChainConfig("line-" + storeType.name().toLowerCase(), true, storeType, props);
    }

    private static void assertRoundTrip(ArtifactStore store, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        String hash = store.put(bytes);

        assertThat(store.exists(hash)).isTrue();
        assertThat(store.get(hash)).get().satisfies(artifact -> {
            assertThat(artifact.hashHex()).isEqualTo(hash);
            assertThat(artifact.size()).isEqualTo(bytes.length);
            try (var input = artifact.openStream()) {
                assertThat(input.readAllBytes()).isEqualTo(bytes);
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    private record DriverManagerBackedDataSource(String url,
                                                 String username,
                                                 String password)
            implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
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

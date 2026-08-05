package io.github.gear4jtest.external.jdbc.spi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;

import io.github.gear4jtest.external.api.spi.ArtifactStorePlugin;
import io.github.gear4jtest.external.jdbc.artifact.DatabaseArtifactStore;
import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatabaseArtifactStorePluginTest {
    @TempDir
    Path tempDirectory;

    private final DataSource dataSource = mock(DataSource.class);
    private final ArtifactStorePlugin.Context context = key -> dataSource;

    @Test
    void build_shouldRequireAnExplicitDialect() {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();

        // When / Then
        assertThatThrownBy(() -> plugin.build(Map.of(), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires property 'dialect'");
    }

    @Test
    void build_shouldAcceptASupportedExplicitDialect() {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();

        // When
        var store = plugin.build(Map.of("dialect", "postgresql"), context);

        // Then
        assertThat(store).isInstanceOf(DatabaseArtifactStore.class);
    }

    @Test
    void build_shouldApplyArtifactSizeLimit() throws Exception {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();
        DatabaseArtifactStore store = (DatabaseArtifactStore) plugin.build(
                                                                           Map.of("dialect", "h2",
                                                                                  "maxArtifactSizeBytes", "3"),
                                                                           context);

        // When / Then
        assertThatThrownBy(() -> store.put(new byte[4]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("maxBytes=3");
        verifyNoInteractions(dataSource);
    }

    @Test
    void build_shouldRejectInvalidArtifactSizeLimit() {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();

        // When / Then
        assertThatThrownBy(() -> plugin.build(Map.of("dialect", "h2", "maxArtifactSizeBytes", "-2"), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxArtifactSizeBytes");
    }

    @Test
    void build_shouldApplySpoolPolicy() {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();
        DatabaseArtifactStore store = (DatabaseArtifactStore) plugin.build(
                                                                           Map.of("dialect", "h2", "spoolDirectory",
                                                                                  tempDirectory.toString(),
                                                                                  "spoolMaxBytes", "3",
                                                                                  "spoolStaleFileAge", "PT1H"),
                                                                           context);

        // When / Then
        assertThatThrownBy(() -> store.put(new byte[4]))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("spool quota exceeded");
        assertThat(store.snapshotSpoolStats().quotaRejections()).isEqualTo(1L);
        verifyNoInteractions(dataSource);
    }

    @Test
    void build_shouldRejectInvalidSpoolDuration() {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();

        // When / Then
        assertThatThrownBy(() -> plugin.build(Map.of("dialect", "h2", "spoolStaleFileAge", "24 hours"), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("spoolStaleFileAge");
    }

    @Test
    void build_shouldRejectInvalidPrivatePermissionRequirement() {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();

        // When / Then
        assertThatThrownBy(() -> plugin.build(Map.of("dialect", "h2", "requirePrivatePermissions", "sometimes"),
                                              context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requirePrivatePermissions")
                .hasMessageContaining("true or false");
    }

    @Test
    void build_shouldResolveExplicitTransactionOperations() throws Exception {
        // Given
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(1);
        AtomicBoolean transactionInvoked = new AtomicBoolean();
        JdbcTransactionOperations transactions = work -> {
            transactionInvoked.set(true);
            work.execute(connection);
        };
        ArtifactStorePlugin.Context explicitContext = key -> switch (key) {
            case "datasource.default" -> dataSource;
            case "transactions.external" -> transactions;
            default -> null;
        };
        DatabaseArtifactStore store = (DatabaseArtifactStore) plugin.build(
                                                                           Map.of("dialect", "h2",
                                                                                  "transactionOperations",
                                                                                  "transactions.external",
                                                                                  "spoolDirectory",
                                                                                  tempDirectory.toString()),
                                                                           explicitContext);

        // When
        store.put("artifact".getBytes(StandardCharsets.UTF_8));

        // Then
        assertThat(transactionInvoked).isTrue();
        verifyNoInteractions(dataSource);
    }

    @Test
    void build_shouldRejectInvalidTransactionOperationsLookup() {
        DatabaseArtifactStorePlugin plugin = new DatabaseArtifactStorePlugin();

        assertThatThrownBy(() -> plugin.build(Map.of("dialect", "h2", "transactionOperations", " "), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lookup key must not be blank");
        assertThatThrownBy(() -> plugin.build(
                                              Map.of("dialect", "h2", "transactionOperations", "missing"),
                                              context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires JdbcTransactionOperations")
                .hasMessageContaining("missing");
    }
}

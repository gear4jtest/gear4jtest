package io.github.gear4jtest.external.jdbc.spi;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;

import io.github.gear4jtest.external.api.artifact.ArtifactSpoolPolicy;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.spi.ArtifactStorePlugin;
import io.github.gear4jtest.external.api.spi.ArtifactStorePropertySchema;
import io.github.gear4jtest.external.jdbc.artifact.DatabaseArtifactStore;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;

public final class DatabaseArtifactStorePlugin implements ArtifactStorePlugin {
    private static final ArtifactStorePropertySchema PROPERTY_SCHEMA = ArtifactStorePropertySchema.closed(
                                                                                                          "datasource",
                                                                                                          "table",
                                                                                                          "dialect",
                                                                                                          "maxArtifactSizeBytes",
                                                                                                          "spoolDirectory",
                                                                                                          "spoolMaxBytes",
                                                                                                          "spoolStaleFileAge",
                                                                                                          "requirePrivatePermissions",
                                                                                                          "transactionOperations");

    @Override
    public String type() {
        return "DATABASE";
    }

    @Override
    public ArtifactStorePropertySchema propertySchema() {
        return PROPERTY_SCHEMA;
    }

    @Override
    public ArtifactStore build(Map<String, String> props, Context ctx) {
        validateProperties(props);
        String dataSourceKey = props == null ? "datasource.default"
                : props.getOrDefault("datasource", "datasource.default");
        Object candidate = ctx.lookup(dataSourceKey);
        if (!(candidate instanceof DataSource dataSource)) {
            throw new IllegalArgumentException(
                    "DATABASE artifact store requires a DataSource in context key: " + dataSourceKey);
        }
        String table = props == null ? null : props.get("table");
        String spoolDirectory = props == null ? null : props.get("spoolDirectory");
        ArtifactSpoolPolicy spoolPolicy = ArtifactSpoolPolicy.builder()
                .directory(spoolDirectory == null || spoolDirectory.isBlank() ? null : Path.of(spoolDirectory))
                .maxBytes(requireLong(props, "spoolMaxBytes", ArtifactSpoolPolicy.DEFAULT_MAX_BYTES))
                .staleFileAge(requireDuration(props, "spoolStaleFileAge",
                                              ArtifactSpoolPolicy.DEFAULT_STALE_FILE_AGE))
                .requirePrivatePermissions(requireBoolean(props, "requirePrivatePermissions",
                                                          ArtifactSpoolPolicy.DEFAULT_REQUIRE_PRIVATE_PERMISSIONS))
                .build();
        DatabaseArtifactStore.Builder builder = DatabaseArtifactStore.builder()
                .dataSource(dataSource)
                .table(table)
                .databaseDialect(requireDialect(props))
                .maxArtifactSizeBytes(requireMaxArtifactSize(props))
                .spoolPolicy(spoolPolicy);
        JdbcTransactionOperations transactionOperations = resolveTransactionOperations(props, ctx);
        if (transactionOperations != null) {
            builder.transactionOperations(transactionOperations);
        }
        return builder.build();
    }

    private static JdbcTransactionOperations resolveTransactionOperations(Map<String, String> props, Context ctx) {
        String key = props == null ? null : props.get("transactionOperations");
        if (key == null) {
            return null;
        }
        if (key.isBlank()) {
            throw new IllegalArgumentException(
                    "DATABASE artifact store transactionOperations lookup key must not be blank");
        }
        Object candidate = ctx.lookup(key);
        if (!(candidate instanceof JdbcTransactionOperations transactionOperations)) {
            throw new IllegalArgumentException("DATABASE artifact store requires JdbcTransactionOperations in "
                    + "context key: " + key);
        }
        return transactionOperations;
    }

    private static long requireLong(Map<String, String> props, String property, long defaultValue) {
        String value = props == null ? null : props.get(property);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid DATABASE artifact store " + property + ": " + value,
                    exception);
        }
    }

    private static Duration requireDuration(Map<String, String> props, String property, Duration defaultValue) {
        String value = props == null ? null : props.get(property);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Duration.parse(value.trim());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid DATABASE artifact store " + property + ": " + value
                    + ". Expected an ISO-8601 duration such as PT24H.", exception);
        }
    }

    private static boolean requireBoolean(Map<String, String> props, String property, boolean defaultValue) {
        String value = props == null ? null : props.get(property);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        throw new IllegalArgumentException("Invalid DATABASE artifact store " + property + ": " + value
                + ". Expected true or false.");
    }

    private static long requireMaxArtifactSize(Map<String, String> props) {
        String value = props == null ? null : props.get("maxArtifactSizeBytes");
        if (value == null || value.isBlank()) {
            return ArtifactStore.DEFAULT_MAX_ARTIFACT_SIZE_BYTES;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed < ArtifactStore.UNLIMITED_SIZE) {
                throw new IllegalArgumentException("must be -1 or >= 0");
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid DATABASE artifact store maxArtifactSizeBytes: " + value
                    + ". Expected -1 or a non-negative byte count.", exception);
        }
    }

    private static Gear4jDatabaseDialect requireDialect(Map<String, String> props) {
        String value = props == null ? null : props.get("dialect");
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DATABASE artifact store requires property 'dialect'. Supported values: "
                    + Arrays.toString(Gear4jDatabaseDialect.values()));
        }
        try {
            return Gear4jDatabaseDialect.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported DATABASE artifact store dialect: " + value
                    + ". Supported values: " + Arrays.toString(Gear4jDatabaseDialect.values()), exception);
        }
    }
}

package io.github.gear4jtest.external.api.spi;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;

import io.github.gear4jtest.core.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.external.api.artifact.ArtifactStore;
import io.github.gear4jtest.external.api.artifact.DatabaseArtifactStore;

public final class DatabaseArtifactStorePlugin implements ArtifactStorePlugin {
    @Override
    public String type() {
        return "DATABASE";
    }

    @Override
    public ArtifactStore build(Map<String, String> props, Context ctx) {
        String dataSourceKey = props == null ? "datasource.default"
                : props.getOrDefault("datasource", "datasource.default");
        Object candidate = ctx.lookup(dataSourceKey);
        if (!(candidate instanceof DataSource dataSource)) {
            throw new IllegalArgumentException(
                    "DATABASE artifact store requires a DataSource in context key: " + dataSourceKey);
        }
        String table = props == null ? null : props.get("table");
        return new DatabaseArtifactStore(dataSource, table, requireDialect(props));
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

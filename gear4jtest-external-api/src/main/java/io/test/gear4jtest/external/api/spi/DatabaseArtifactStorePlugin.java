package io.test.gear4jtest.external.api.spi;

import java.util.Map;
import javax.sql.DataSource;

import io.test.gear4jtest.external.api.artifact.ArtifactStore;
import io.test.gear4jtest.external.api.artifact.DatabaseArtifactStore;

public final class DatabaseArtifactStorePlugin implements ArtifactStorePlugin {
    @Override
    public String type() {
        return "DATABASE";
    }

    @Override
    public ArtifactStore build(Map<String, String> props, Context ctx) {
        String dataSourceKey = props == null ? null : props.getOrDefault("datasource", "datasource.default");
        Object candidate = ctx.lookup(dataSourceKey);
        if (!(candidate instanceof DataSource dataSource)) {
            throw new IllegalArgumentException(
                    "DATABASE artifact store requires a DataSource in context key: " + dataSourceKey);
        }
        String table = props == null ? null : props.get("table");
        return new DatabaseArtifactStore(dataSource, table);
    }
}

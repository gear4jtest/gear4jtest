package io.github.gear4jtest.studio.demo;

import java.nio.file.Path;
import javax.sql.DataSource;

import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.http.RequestActorContext;
import io.github.gear4jtest.studio.jdbc.JdbcStudioStore;
import io.github.gear4jtest.studio.jdbc.StudioJdbcSchema;
import io.github.gear4jtest.studio.management.StudioManagementService;
import io.github.gear4jtest.studio.spi.StudioPublisher;
import io.github.gear4jtest.studio.spi.StudioRuntime;
import org.h2.jdbcx.JdbcDataSource;

/**
 * Local durable H2 example. Real hosts can supply a configured DataSource and
 * manage migrations themselves.
 */
public final class DemoDatabase {
    private DemoDatabase() {
    }

    public static DataSource fromEnvironment(String host) {
        String setting = System.getenv("GEAR4J_STUDIO_DB_FILE");
        String path = Path.of(setting == null ? "studio-data/" + host : setting).toAbsolutePath().normalize()
                .toString();
        if (path.contains(";"))
            throw new IllegalArgumentException("Invalid database path");
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:file:" + path + ";DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000");
        source.setUser("studio");
        String password = System.getenv("GEAR4J_STUDIO_DB_PASSWORD");
        source.setPassword(password == null ? "" : password);
        return source;
    }

    public static StudioManagementService service(javax.sql.DataSource source,
                                                  StudioRuntime runtime,
                                                  RequestActorContext actors) {
        return service(source, runtime, actors, null);
    }

    public static StudioManagementService service(DataSource source,
                                                  StudioRuntime runtime,
                                                  RequestActorContext actors,
                                                  StudioPublisher publisher) {
        StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.H2);
        return new StudioManagementService(new JdbcStudioStore(source), runtime, actors,
                a -> a.groups().contains("domain-creators"), java.time.Clock.systemUTC(), publisher,
                io.github.gear4jtest.studio.management.TestReceiptPolicy.defaults(), new DemoScenarioPolicy());
    }
}

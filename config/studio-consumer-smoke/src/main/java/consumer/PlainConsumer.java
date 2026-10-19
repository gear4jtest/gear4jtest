package consumer;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.http.StudioManagementHttpHandler;
import io.github.gear4jtest.studio.jdbc.JdbcStudioStore;
import io.github.gear4jtest.studio.jdbc.StudioJdbcSchema;
import io.github.gear4jtest.studio.management.StudioManagementService;
import io.github.gear4jtest.studio.model.ChainDefinition;
import io.github.gear4jtest.studio.management.StudioIdentity;
import io.github.gear4jtest.studio.runtime.LocalGear4jRuntime;
import io.github.gear4jtest.studio.runtime.OperationCatalog;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.h2.jdbcx.JdbcDataSource;

public final class PlainConsumer {
    private PlainConsumer() { }
    public static LocalGear4jRuntime runtime() {
        return new LocalGear4jRuntime("consumer.context", "release-smoke", new OperationCatalog(List.of()),
                null, null, DataCapturePolicy.none(), Duration.ofSeconds(5));
    }
    public static void main(String[] args) throws Exception {
        try (var runtime = runtime()) { verify(runtime); }
    }
    public static void verify(LocalGear4jRuntime runtime) throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:consumer_" + UUID.randomUUID());
        try (var keepAlive = source.getConnection()) {
            StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.H2);
            var actor = StudioIdentity.fromScopedSubject("https://issuer.example", "consumer", Set.of(), false);
            var service = new StudioManagementService(new JdbcStudioStore(source), runtime, () -> actor,
                    a -> true, Clock.systemUTC());
            var domain = service.createDomain("consumer", "Consumer", "").resource();
            var chain = service.createChain(domain.id(), "chain", "Chain", "", "consumer_chain").resource();
            var definition = new ChainDefinition(1, "consumer_chain", "java.lang.String", "java.lang.String", List.of());
            var draft = service.createDraft(chain.id(), "Draft", definition);
            if (!definition.equals(service.draft(draft.draft().id()).revision().definition()))
                throw new AssertionError("Durable public API round trip failed");
            if (!"2".equals(StudioManagementHttpHandler.PROTOCOL_VERSION))
                throw new AssertionError("Unexpected HTTP protocol");
            System.out.println("Independent consumer passed: " + runtime.context().id());
        }
    }
}

package io.github.gear4jtest.studio.demo;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.jdbc.JdbcStudioStore;
import io.github.gear4jtest.studio.jdbc.StudioJdbcSchema;
import io.github.gear4jtest.studio.management.StudioIdentity;
import io.github.gear4jtest.studio.management.StudioManagementService;
import io.github.gear4jtest.studio.model.*;
import io.github.gear4jtest.studio.runtime.*;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ModeAwareCatalogTest {
    @Test
    void oneFrozenArtifactUsesDifferentClassesAndGettersInTestAndRun() throws Exception {
        // Given
        var descriptor = new OperationDescriptor("pricing.lookup", "Pricing", "Mode-aware fixture", "Synthetic",
                DataContract.text(), DataContract.text(), true, true,
                Map.of("prefix",
                       new OperationDescriptor.Parameter("Prefix", ParameterValue.Kind.TEXT, true, List.of())));
        var test = new RegisteredOperation.Binding(PrefixOperation.class, Map.of("prefix", "getPrefix"));
        var run = new RegisteredOperation.Binding(RunPrefixOperation.class, Map.of("prefix", "getLabel"));
        var operation = new RegisteredOperation(descriptor, Map.of(ExecutionMode.TEST, test, ExecutionMode.RUN, run),
                String.class, String.class);
        var catalog = new OperationCatalog(List.of(operation));
        assertThatThrownBy(() -> new RegisteredOperation(descriptor,
                Map.of(ExecutionMode.TEST, test, ExecutionMode.RUN,
                       new RegisteredOperation.Binding(LengthOperation.class, Map.of())),
                String.class, String.class))
                .isInstanceOf(IllegalArgumentException.class);
        var same = new OperationCatalog(List.of(new RegisteredOperation(descriptor,
                Map.of(ExecutionMode.TEST, test, ExecutionMode.RUN, test), String.class, String.class)));
        assertThat(catalog.fingerprint()).isNotEqualTo(same.fingerprint());
        ResourceFactory resources = new ResourceFactory() {
            @Override
            public <T> T getResource(Class<T> type) {
                return type.cast(type == RunPrefixOperation.class ? new RunPrefixOperation() : new PrefixOperation());
            }
        };
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        StudioJdbcSchema.migrate(source, Gear4jDatabaseDialect.H2);
        var executor = DemoWiring.plainExecutor(resources);
        try (var runtime = new LocalGear4jRuntime("mode-aware", "test", catalog, executor, resources,
                DataCapturePolicy.allowed(), Duration.ofSeconds(5));
                var host = new DemoPublicationHost(source, catalog)) {
            var service = new StudioManagementService(new JdbcStudioStore(source), runtime,
                    () -> new StudioIdentity("alice", Set.of(), false), a -> true, host.publisher());
            var domain = service.createDomain("domain", "Domain", "").resource();
            var chain = service.createChain(domain.id(), "pricing", "Pricing", "", "pricing").resource();
            var definition = new ChainDefinition(1, "pricing", "java.lang.String", "java.lang.String",
                    List.of(new ChainDefinition.Operation("lookup", "pricing.lookup",
                            Map.of("prefix", new ParameterValue(ParameterValue.Kind.TEXT, "price:")))));
            var draft = service.createDraft(chain.id(), "Draft", definition);
            var version = service.createVersion(draft.draft().id(), 1, "1").version();
            // When / Then
            assertThat(version.source()).contains("parameter:prefix").doesNotContain("PrefixOperation");
            assertThat(version.definitionSchemaVersion()).isEqualTo(1);
            assertThat(version.formatAdapterId()).isEqualTo("gear4j-studio-xml");
            assertThat(version.formatAdapterVersion()).isEqualTo("2");
            assertThat(service.testVersion(version.id(), UUID.randomUUID(), "car").run().output().value())
                    .isEqualTo("price:car");
            service.publishTest(version.id());
            service.promoteRun(version.id());
            var line = host.manager().getOperationChain("pricing", "1", ExecutionMode.RUN).getAssemblyLineDefinition();
            var executed = (io.github.gear4jtest.core.api.ExecutionResult<?>) io.github.gear4jtest.core.api.AssemblyLineExecutor.class
                    .getMethod("execute", io.github.gear4jtest.core.api.AssemblyLine.class, RunRequest.class)
                    .invoke(executor, line, RunRequest.builder().input("car").resourceFactory(resources).build());
            assertThat(executed.getResult()).isEqualTo("RUN:price:car");
            assertThat(host.objects().find("pricing", "1", ExecutionMode.RUN).orElseThrow().contentHash())
                    .isEqualTo(version.artifactHash());
        } finally {
            try (var c = source.getConnection(); var s = c.createStatement()) {
                s.execute("SHUTDOWN");
            }
        }
    }
}

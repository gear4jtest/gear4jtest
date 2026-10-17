package io.github.gear4jtest.studio.demo;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import io.github.gear4jtest.external.api.AssemblyLineManager;
import io.github.gear4jtest.external.api.StoreType;
import io.github.gear4jtest.external.api.compiler.JavaxToolsGeneratedSourceCompiler;
import io.github.gear4jtest.external.api.loader.InMemoryClassLoaderRegistry;
import io.github.gear4jtest.external.api.model.OperationChainConfig;
import io.github.gear4jtest.external.api.repository.OperationChainObjectRepository;
import io.github.gear4jtest.external.api.translator.OperationChainTranslatorResolver;
import io.github.gear4jtest.external.jdbc.artifact.DatabaseArtifactStore;
import io.github.gear4jtest.external.jdbc.repository.ExternalJdbcSchemaMigrator;
import io.github.gear4jtest.external.jdbc.repository.OperationChainConfigRepositoryJdbc;
import io.github.gear4jtest.external.jdbc.repository.OperationChainObjectRepositoryJdbc;
import io.github.gear4jtest.external.jdbc.repository.OperationChainTagRepositoryJdbc;
import io.github.gear4jtest.jdbc.persistence.Gear4jDatabaseDialect;
import io.github.gear4jtest.studio.runtime.Gear4jPublicationAdapter;
import io.github.gear4jtest.studio.runtime.OperationCatalog;
import io.github.gear4jtest.studio.runtime.StudioXmlAdapter;
import io.github.gear4jtest.studio.spi.StudioPublisher;

/**
 * Local host wiring; the application owns the manager, never the management
 * service.
 */
public final class DemoPublicationHost implements AutoCloseable {
    private final AssemblyLineManager manager;
    private final OperationChainObjectRepository objects;
    private final StudioPublisher publisher;

    public DemoPublicationHost(DataSource source, OperationCatalog catalog) {
        var dialect = Gear4jDatabaseDialect.H2;
        ExternalJdbcSchemaMigrator.forDialect(dialect).migrate(source);
        var configurations = OperationChainConfigRepositoryJdbc.builder().dataSource(source).databaseDialect(dialect)
                .build();
        objects = OperationChainObjectRepositoryJdbc.builder().dataSource(source).databaseDialect(dialect).build();
        var tags = OperationChainTagRepositoryJdbc.builder().dataSource(source).databaseDialect(dialect).build();
        manager = AssemblyLineManager.builder().configRepository(configurations).objectRepository(objects)
                .tagRepository(tags)
                .storeProvider(config -> {
                    if (!config.storeType().equals(StoreType.DATABASE))
                        throw new IllegalStateException("This demo only accepts its configured database store");
                    return DatabaseArtifactStore.builder().dataSource(source).databaseDialect(dialect)
                            .maxArtifactSizeBytes(StudioXmlAdapter.MAX_BYTES).build();
                }).classLoaderRegistry(InMemoryClassLoaderRegistry.builder().build())
                .translatorResolver(new OperationChainTranslatorResolver(
                        List.of(Gear4jPublicationAdapter.translator(catalog))))
                .compiler(new JavaxToolsGeneratedSourceCompiler(DemoPublicationHost.class.getClassLoader()))
                .maxArtifactSizeBytes(StudioXmlAdapter.MAX_BYTES).build();
        publisher = new Gear4jPublicationAdapter(manager, objects, catalog, id -> {
            if (configurations.findByAssemblyLineId(id).isEmpty())
                configurations.upsert(new OperationChainConfig(id, false, StoreType.DATABASE, Map.of()));
        });
    }

    public StudioPublisher publisher() {
        return publisher;
    }

    AssemblyLineManager manager() {
        return manager;
    }

    OperationChainObjectRepository objects() {
        return objects;
    }

    @Override
    public void close() {
        manager.close();
    }
}

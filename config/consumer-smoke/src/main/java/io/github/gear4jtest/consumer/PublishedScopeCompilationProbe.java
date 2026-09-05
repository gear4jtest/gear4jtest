package io.github.gear4jtest.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.AssemblyLineExecutor;
import io.github.gear4jtest.core.persistence.PersistenceFlushObservation;
import io.github.gear4jtest.core.persistence.PersistenceFlushObserver;
import io.github.gear4jtest.core.persistence.PersistenceFlushSubscription;
import io.github.gear4jtest.core.persistence.PersistenceRuntimeMonitor;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.external.jdbc.repository.ExternalJdbcSchemaMigrator;
import io.github.gear4jtest.jackson.JacksonPayloadCloner;
import io.github.gear4jtest.jdbc.persistence.JdbcTransactionOperations;
import io.github.gear4jtest.jdbc.persistence.PersistenceJsonCodec;
import io.github.gear4jtest.micrometer.PersistenceMetricsBinder;
import io.github.gear4jtest.spring.SpringResourceFactory;
import io.github.gear4jtest.spring.boot.SpringJdbcTransactionOperations;
import io.github.gear4jtest.spring.boot.actuate.Gear4jPersistenceHealthIndicator;
import io.github.gear4jtest.xml.translator.XmlOperationChainTranslator;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * Compilation-only probe for types exposed transitively by published module APIs.
 * The consumer deliberately declares no direct dependency on those libraries.
 */
final class PublishedScopeCompilationProbe {
    private @Nullable String nullableValue;
    private AssemblyLine<?, ?> assemblyLine;
    private AssemblyLineExecutor assemblyLineExecutor;
    private OperationChainTranslator translator;
    private ExternalJdbcSchemaMigrator externalJdbcSchemaMigrator;
    private ObjectMapper objectMapper;
    private ApplicationContext applicationContext;
    private MeterRegistry meterRegistry;
    private HealthIndicator healthIndicator;
    private PersistenceFlushObservation persistenceFlushObservation;
    private PersistenceFlushObserver persistenceFlushObserver;
    private PersistenceFlushSubscription persistenceFlushSubscription;
    private PersistenceRuntimeMonitor persistenceRuntimeMonitor;
    private PersistenceJsonCodec persistenceJsonCodec;
    private JdbcTransactionOperations jdbcTransactionOperations;

    private JacksonPayloadCloner jacksonPayloadCloner;
    private XmlOperationChainTranslator xmlTranslator;
    private SpringResourceFactory springResourceFactory;
    private PersistenceMetricsBinder persistenceMetricsBinder;
    private Gear4jPersistenceHealthIndicator persistenceHealthIndicator;
    private SpringJdbcTransactionOperations springJdbcTransactionOperations;
    private DataSourceTransactionManager dataSourceTransactionManager;
}

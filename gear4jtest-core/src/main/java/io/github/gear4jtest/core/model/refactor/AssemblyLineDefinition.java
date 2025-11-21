package io.github.gear4jtest.core.model.refactor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.DatabaseExecutionManager;
import io.github.gear4jtest.core.execution.InMemoryExecutionManager;
import io.github.gear4jtest.core.execution.PipelineExecutionManager;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.EventHandlingDefinition;
import io.github.gear4jtest.core.persistence.OperationExecutionRecord;
import io.github.gear4jtest.core.persistence.PipelineExecution;

@SuppressWarnings("unchecked")
public class AssemblyLineDefinition<IN, OUT> {

    private final String id;
    private final List<OperationDefinition<?, ?>> operations;
    private final Map<String, Object> context;
    private final ResourceFactory resourceFactory;
    private final Configuration configuration; // config "par défaut" issue du builder

    public AssemblyLineDefinition(String id,
                                  List<OperationDefinition<?, ?>> operations,
                                  Map<String, Object> context,
                                  ResourceFactory resourceFactory,
                                  Configuration configuration) {
        this.id = Objects.requireNonNull(id, "id");
        this.operations = operations != null ? new ArrayList<>(operations) : List.of();
        this.context = context != null ? new HashMap<>(context) : new HashMap<>();
//        this.resourceFactory = Objects.requireNonNull(resourceFactory, "resourceFactory");
        this.resourceFactory = resourceFactory;
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public static <IN, OUT> Builder<IN, OUT> builder() {
        return Builder.create();
    }

    public ExecutionResult<OUT> execute(IN input,
                                        Map<String, Object> context,
                                        ResourceFactory resourceFactory) {
        EventManager eventManager = null;
        try {
            // Event buses éventuels (peuvent être null)
            eventManager = new EventManager(
                    configuration != null && configuration.getEventHandlingDefinition() != null
                            ? configuration.getEventHandlingDefinition().getEventBuses()
                            : null
            );

            // Choix du manager (IN_MEMORY / DATABASE ou custom via configuration)
            PipelineExecutionManager manager = resolveManager(configuration);

            // ⚠️ ExecutionContext doit avoir un ctor étendu :
            // new ExecutionContext(pipelineId, eventManager, resourceFactory, manager)
            ExecutionContext executionContext =
                    new ExecutionContext(id, eventManager, resourceFactory, manager);

            // Création de l’exécution globale
            PipelineExecution execution =
                    new PipelineExecution(executionContext.getExecutionId(), id, new HashMap<>(context));
            manager.start(execution);

            Object current = input;

            for (OperationDefinition<?, ?> op : operations) {
                @SuppressWarnings("unchecked")
                OperationDefinition<Object, Object> a = (OperationDefinition<Object, Object>) op;

                // 👉 run(...) retourne maintenant un OperationExecutionRecord
                OperationExecutionRecord rec = a.run(current, executionContext);

                // persistance au fil de l’eau
                manager.append(rec);

                // arrêt en cas d’échec ou de STOP
                if (rec.getStatus() == OperationExecutionRecord.Status.FAILED
                        || rec.getStatus() == OperationExecutionRecord.Status.STOPPED) {

                    execution.setContext(executionContext.getContext());
                    execution.setEndTime(Instant.now());
                    if (configuration.getPersistence() != null
                            && configuration.getPersistence().isStoreResultObject()) {
                        execution.setResult(null);
                    }
                    manager.end(execution);

                    return new ExecutionResult<>(
                            executionContext.getExecutionId(),
                            null,
                            false,
                            null
                    );
                }

                // chaînage : on utilise la sortie transiente du record
                current = rec.getOutput(Object.class);
            }

            // fin OK
            execution.setContext(executionContext.getContext());
            execution.setEndTime(Instant.now());
            if (configuration.getPersistence() != null
                    && configuration.getPersistence().isStoreResultObject()) {
                execution.setResult(current);
            }
            manager.end(execution);

            return new ExecutionResult<>(
                    executionContext.getExecutionId(),
                    (OUT) current,
                    true,
                    null
            );

        } catch (Exception e) {
            // garde-fou : en cas d’erreur non gérée
            return new ExecutionResult<>(UUID.randomUUID(), null, false, e);
        } finally {
            if (eventManager != null) {
                eventManager.shutdown();
            }
        }
    }

    // ---------- Persistance de l'exécution ----------
    private void saveExecutionStart(PipelineExecutionManager manager, PipelineExecution execution) {
        manager.start(execution);
    }

    private void saveExecutionEnd(PipelineExecutionManager manager,
                                  Configuration effectiveConfig,
                                  PipelineExecution execution,
                                  ExecutionContext executionContext,
                                  Object result) {
        execution.setContext(executionContext.getContext());
        execution.setEndTime(Instant.now());
        if (effectiveConfig.persistence != null && effectiveConfig.persistence.isStoreResultObject()) {
            execution.setResult(result);
        }
        manager.end(execution);
    }

    // ---------- Résolution du manager (in-memory / DB) ----------
    private PipelineExecutionManager resolveManager(Configuration cfg) {
        if (cfg != null && cfg.persistence != null) {
            final var p = cfg.persistence;
            if (p.getPersistenceType() == PersistenceConfiguration.PersistenceType.DATABASE) {
                DataSource ds = p.getDataSource();
                return new DatabaseExecutionManager(ds);
            } else {
                return new InMemoryExecutionManager();
            }
        }
        return new InMemoryExecutionManager();
    }

    // --------------------------------------------------------------------------------------------
    // Builder (conservé pour compatibilité avec les ElementModelBuilders)
    // --------------------------------------------------------------------------------------------
    public static class Builder<IN, OUT> {
        private final List<OperationDefinition<?, ?>> operations = new ArrayList<>();
        private final Map<String, Object> context = new HashMap<>();
        private String id;
        private ResourceFactory resourceFactory;
        private Configuration configuration = Configuration.builder().build();

        public static <IN, OUT> Builder<IN, OUT> create() {
            return new Builder<>();
        }

        public Builder<IN, OUT> id(String id) {
            this.id = id;
            return this;
        }

        public Builder<IN, OUT> resourceFactory(ResourceFactory factory) {
            this.resourceFactory = factory;
            return this;
        }

        public Builder<IN, OUT> configuration(Configuration configuration) {
            this.configuration = configuration;
            return this;
        }

        public Builder<IN, OUT> persistence(PersistenceConfiguration persistence) {
            if (this.configuration == null) this.configuration = new Configuration();
            this.configuration.persistence = persistence;
            return this;
        }

        public Builder<IN, OUT> eventHandling(EventHandlingDefinition def) {
            if (this.configuration == null) this.configuration = new Configuration();
            this.configuration.eventHandlingDefinition = def;
            return this;
        }

        public Builder<IN, OUT> putContext(String key, Object value) {
            this.context.put(key, value);
            return this;
        }

        public Builder<IN, OUT> context(Map<String, Object> ctx) {
            this.context.clear();
            if (ctx != null) this.context.putAll(ctx);
            return this;
        }

        public <T> Builder<IN, T> then(OperationDefinition<OUT, T> operation) {
            Objects.requireNonNull(operation);
            this.operations.add(operation);
            return (Builder<IN, T>) this;
        }

        public AssemblyLineDefinition<IN, OUT> build() {
            Objects.requireNonNull(id, "id");
//            Objects.requireNonNull(resourceFactory, "resourceFactory");
            Objects.requireNonNull(configuration, "configuration");
            return new AssemblyLineDefinition<>(id, operations, context, resourceFactory, configuration);
        }
    }

    // --------------------------------------------------------------------------------------------
    // Configuration (et son builder)
    // --------------------------------------------------------------------------------------------
    public static class Configuration {
        private PersistenceConfiguration persistence;
        private EventHandlingDefinition eventHandlingDefinition;

        public static ConfigBuilder builder() {
            return new ConfigBuilder();
        }

        public PersistenceConfiguration getPersistence() {
            return persistence;
        }

        public EventHandlingDefinition getEventHandlingDefinition() {
            return eventHandlingDefinition;
        }

        public static class ConfigBuilder {
            private final Configuration managed = new Configuration();

            public ConfigBuilder persistence(PersistenceConfiguration persistence) {
                managed.persistence = persistence;
                return this;
            }

            public ConfigBuilder eventHandling(EventHandlingDefinition def) {
                managed.eventHandlingDefinition = def;
                return this;
            }

            public Configuration build() {
                return managed;
            }
        }
    }
}

package io.github.gear4jtest.core.model.refactor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.PipelineExecutionManager;
import io.github.gear4jtest.core.factory.ResourceFactory;
import io.github.gear4jtest.core.model.EventHandlingDefinition;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.persistence.OperationExecutionRecord;
import io.github.gear4jtest.core.persistence.PipelineExecution;

public class AssemblyLineDefinition<IN, OUT> {

    private final String id;
    private final List<OperationDefinition<?, ?>> operations;
    private final Map<String, Object> defaultContext;
    private final Configuration configuration;

    private AssemblyLineDefinition(String id,
                                   List<OperationDefinition<?, ?>> operations,
                                   Map<String, Object> defaultContext,
                                   Configuration configuration) {
        this.id = id;
        this.operations = operations != null ? new ArrayList<>(operations) : List.of();
        this.defaultContext = defaultContext != null ? new HashMap<>(defaultContext) : new HashMap<>();
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    public ExecutionResult<OUT> execute(IN input,
                                        Map<String, Object> context,
                                        ResourceFactory resourceFactory,
                                        PipelineExecutionManager manager) {
        EventManager eventManager = null;
        PipelineExecution execution = null;

        Map<String, Object> effectiveContext = new HashMap<>(this.defaultContext);
        if (context != null) {
            effectiveContext.putAll(context);
        }

        try {
            eventManager = new EventManager(
                    Optional.ofNullable(configuration.getEventHandlingDefinition())
                            .map(EventHandlingDefinition::getEventBuses)
                            .orElse(List.of())
            );

            UUID executionId = UUID.randomUUID();
            execution = new PipelineExecution(executionId, id, new HashMap<>(effectiveContext));
            execution.markStarted();
            manager.start(execution);

            ExecutionContext executionContext =
                    new ExecutionContext(executionId, id, eventManager, resourceFactory, manager, execution);
            executionContext.getContext().putAll(effectiveContext);
            executionContext.setCurrentItemId("root");

            var result = executeWithin(executionContext, input);
            if (!result.isSuccess()) {
                return result;
            }

            execution.setContext(executionContext.getContext());
            execution.setEndTime(Instant.now());
            execution.setStatus(ExecutionStatus.SUCCEEDED);
            if (configuration.getPersistence() != null && configuration.getPersistence().isStoreResultObject()) {
                execution.setResult(result.getResult());
            }

            return result;

        } catch (Exception e) {
            return ExecutionResult.failure(e, execution);
        } finally {
            if (manager != null) {
                manager.end(execution);
            }
            if (eventManager != null) {
                eventManager.shutdown();
            }
        }
    }

    @SuppressWarnings("unchecked")
    public ExecutionResult<OUT> executeWithin(ExecutionContext ctx, IN input) {
        Object current = input;
        boolean success = true;
        Exception error = null;

        for (OperationDefinition<?, ?> op : operations) {
            OperationDefinition<Object, Object> a = (OperationDefinition<Object, Object>) op;

            OperationExecutionRecord rec = a.run(current, ctx);

            if (rec.getStatus() == OperationExecutionRecord.Status.FAILED
                    || rec.getStatus() == OperationExecutionRecord.Status.STOPPED) {

                ctx.getPipelineExecution().setContext(ctx.getContext());
                ctx.getPipelineExecution().setEndTime(Instant.now());
                if (configuration.getPersistence() != null
                        && configuration.getPersistence().isStoreResultObject()) {
                    ctx.getPipelineExecution().setResult(null);
                }
                ctx.getExecutionManager().end(ctx.getPipelineExecution());
                success = false;
                break;
            }

            current = rec.getOutput(Object.class);
        }
        OUT out = success ? (OUT) current : null;
        return new ExecutionResult<>(out, success, ctx.getPipelineExecution(), error);
    }

    public static <IN, OUT> Builder<IN, OUT> builder(String id) {
        return new Builder<>(id);
    }

    public static class Builder<IN, OUT> {
        private final String id;
        private final List<OperationDefinition<?, ?>> operations = new ArrayList<>();
        private final Map<String, Object> defaultContext = new HashMap<>();
        private final Configuration.Builder configBuilder = Configuration.builder();

        private Builder(String id) {
            this.id = id;
        }

        public Builder<IN, OUT> persistence(PersistenceConfiguration persistence) {
            this.configBuilder.persistence(persistence);
            return this;
        }

        public Builder<IN, OUT> eventHandling(EventHandlingDefinition def) {
            this.configBuilder.eventHandling(def);
            return this;
        }

        public Builder<IN, OUT> configuration(Configuration config) {
            if (config != null) {
                this.configBuilder.persistence(config.getPersistence());
                this.configBuilder.eventHandling(config.getEventHandlingDefinition());
            }
            return this;
        }

        public Builder<IN, OUT> putContext(String key, Object value) {
            this.defaultContext.put(key, value);
            return this;
        }

        public Builder<IN, OUT> context(Map<String, Object> ctx) {
            if (ctx != null) {
                this.defaultContext.clear();
                this.defaultContext.putAll(ctx);
            }
            return this;
        }

        public <T> Builder<IN, T> then(OperationDefinition<OUT, T> operation) {
            Objects.requireNonNull(operation);
            this.operations.add(operation);
            return (Builder<IN, T>) this;
        }

        public AssemblyLineDefinition<IN, OUT> build() {
            Objects.requireNonNull(id, "id");
            Configuration finalConfig = this.configBuilder.build();
            return new AssemblyLineDefinition<>(id, operations, defaultContext, finalConfig);
        }
    }

    public static class Configuration {

        private final PersistenceConfiguration persistence;
        private final EventHandlingDefinition eventHandlingDefinition;

        private Configuration(PersistenceConfiguration persistence,
                              EventHandlingDefinition eventHandlingDefinition) {
            this.persistence = persistence;
            this.eventHandlingDefinition = eventHandlingDefinition;
        }

        public PersistenceConfiguration getPersistence() {
            return persistence;
        }

        public EventHandlingDefinition getEventHandlingDefinition() {
            return eventHandlingDefinition;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private PersistenceConfiguration persistence;
            private EventHandlingDefinition eventHandlingDefinition;

            public Builder persistence(PersistenceConfiguration persistence) {
                this.persistence = persistence;
                return this;
            }

            public Builder eventHandling(EventHandlingDefinition def) {
                this.eventHandlingDefinition = def;
                return this;
            }

            public Configuration build() {
                return new Configuration(persistence, eventHandlingDefinition);
            }
        }
    }
}

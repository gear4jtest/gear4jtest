package io.github.gear4jtest.core.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.api.pipeline.PipelineRuntimeContract;
import io.github.gear4jtest.core.api.pipeline.PipelineRuntimeContractValidator;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.SequenceStation;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;

public class AssemblyLine<IN, OUT> {

    private static final String DEFAULT_VERSION = "1";

    private final String id;
    private final String version;
    private final AbstractStation<?, ?> rootStation;
    private final Map<String, Object> defaultContext;
    private final Configuration configuration;

    private AssemblyLine(String id,
                         String version,
                         AbstractStation<?, ?> rootStation,
                         Map<String, Object> defaultContext,
                         Configuration configuration) {
        this.id = id;
        this.version = version != null ? version : DEFAULT_VERSION;
        this.rootStation = Objects.requireNonNull(rootStation, "rootStation must not be null");
        this.defaultContext = defaultContext != null ? new HashMap<>(defaultContext) : new HashMap<>();
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    public static <IN, OUT> Builder<IN, OUT> builder(String id) {
        return new Builder<>(id);
    }

    public String getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public AbstractStation<?, ?> getRootStation() {
        return rootStation;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public Map<String, Object> getDefaultContext() {
        return defaultContext;
    }

    public static class Builder<IN, OUT> {
        private final String id;
        private final List<AbstractStation<?, ?>> operations = new ArrayList<>();
        private final Map<String, Object> defaultContext = new HashMap<>();
        private final Configuration.Builder configBuilder = Configuration.builder();
        private String version;

        private Builder(String id) {
            this.id = id;
        }

        public Builder<IN, OUT> version(String version) {
            this.version = version;
            return this;
        }

        public Builder<IN, OUT> persistence(PersistenceConfiguration persistence) {
            this.configBuilder.persistence(persistence);
            return this;
        }

        public Builder<IN, OUT> eventHandling(EventHandlingDefinition def) {
            this.configBuilder.eventHandling(def);
            return this;
        }

        public Builder<IN, OUT> defaultExtension(RuntimeExtension extension) {
            this.configBuilder.defaultExtension(extension);
            return this;
        }

        public Builder<IN, OUT> defaultExtensions(List<RuntimeExtension> extensions) {
            this.configBuilder.defaultExtensions(extensions);
            return this;
        }

        public Builder<IN, OUT> runtimeContract(PipelineRuntimeContract runtimeContract) {
            this.configBuilder.runtimeContract(runtimeContract);
            return this;
        }

        public Builder<IN, OUT> configuration(Configuration config) {
            if (config != null) {
                this.configBuilder.persistence(config.getPersistence());
                this.configBuilder.eventHandling(config.getEventHandlingDefinition());
                this.configBuilder.defaultExtensions(config.getDefaultExtensions());
                this.configBuilder.runtimeContract(config.getRuntimeContract());
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

        @SuppressWarnings("unchecked")
        public <T> Builder<IN, T> then(AbstractStation<OUT, T> operation) {
            Objects.requireNonNull(operation);
            this.operations.add(operation);
            return (Builder<IN, T>) this;
        }

        public AssemblyLine<IN, OUT> build() {
            Objects.requireNonNull(id, "id");
            Configuration finalConfig = this.configBuilder.build();
            AbstractStation<?, ?> root;
            if (operations.isEmpty()) {
                // Pipeline vide : on crée une sequence root vide (SUCCEEDED avec input)
                root = SequenceStation.syntheticRoot(id + ":root", List.of(), FlowConfig.DEFAULT);
            } else if (operations.size() == 1) {
                root = operations.get(0);
            } else {
                root = SequenceStation.syntheticRoot(id + ":root", operations, FlowConfig.DEFAULT);
            }
            return new AssemblyLine<>(id, version, root, defaultContext, finalConfig);
        }
    }

    public static class Configuration {

        private final PersistenceConfiguration persistence;
        private final EventHandlingDefinition eventHandlingDefinition;
        private final List<RuntimeExtension> defaultExtensions;
        private final PipelineRuntimeContract runtimeContract;

        private Configuration(PersistenceConfiguration persistence,
                              EventHandlingDefinition eventHandlingDefinition,
                              List<RuntimeExtension> defaultExtensions,
                              PipelineRuntimeContract runtimeContract) {
            this.persistence = persistence;
            this.eventHandlingDefinition = eventHandlingDefinition;
            this.defaultExtensions = defaultExtensions == null ? List.of() : List.copyOf(defaultExtensions);
            this.runtimeContract = Objects.requireNonNull(runtimeContract, "runtimeContract must not be null");
        }

        public static Builder builder() {
            return new Builder();
        }

        public PersistenceConfiguration getPersistence() {
            return persistence;
        }

        public EventHandlingDefinition getEventHandlingDefinition() {
            return eventHandlingDefinition;
        }

        public List<RuntimeExtension> getDefaultExtensions() {
            return defaultExtensions;
        }

        public PipelineRuntimeContract getRuntimeContract() {
            return runtimeContract;
        }

        public static class Builder {

            private final List<RuntimeExtension> defaultExtensions = new ArrayList<>();
            private PersistenceConfiguration persistence;
            private EventHandlingDefinition eventHandlingDefinition;
            private PipelineRuntimeContract runtimeContract;

            public Builder persistence(PersistenceConfiguration persistence) {
                this.persistence = persistence;
                return this;
            }

            public Builder defaultExtension(RuntimeExtension extension) {
                if (extension != null) {
                    this.defaultExtensions.add(extension);
                }
                return this;
            }

            public Builder defaultExtensions(List<RuntimeExtension> extensions) {
                if (extensions != null) {
                    this.defaultExtensions.addAll(extensions);
                }
                return this;
            }

            public Builder eventHandling(EventHandlingDefinition def) {
                this.eventHandlingDefinition = def;
                return this;
            }

            public Builder runtimeContract(PipelineRuntimeContract runtimeContract) {
                this.runtimeContract = runtimeContract;
                return this;
            }

            public Configuration build() {
                PipelineRuntimeContract finalRuntimeContract = runtimeContract != null ? runtimeContract
                        : defaultRuntimeContract();
                PipelineRuntimeContractValidator.validateConfigurationCoherence(finalRuntimeContract, persistence,
                                                                                eventHandlingDefinition,
                                                                                defaultExtensions);
                return new Configuration(persistence, eventHandlingDefinition, defaultExtensions, finalRuntimeContract);
            }

            private PipelineRuntimeContract defaultRuntimeContract() {
                boolean hasRuntimeConfiguration = persistence != null || eventHandlingDefinition != null
                        || !defaultExtensions.isEmpty();
                return hasRuntimeConfiguration ? PipelineRuntimeContract.nestedRunOnly()
                        : PipelineRuntimeContract.inlineConfigless();
            }
        }
    }
}

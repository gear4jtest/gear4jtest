package io.github.gear4jtest.core.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.core.api.assemblyline.AssemblyLineRuntimeContract;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineRuntimeContractValidator;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.config.FlowConfig;
import io.github.gear4jtest.core.api.config.PersistenceConfiguration;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.SequenceStation;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;

/**
 * Definition of a Gear4J pipeline.
 *
 * <p>
 * An {@code AssemblyLine} describes what should be executed: its identity,
 * version, root station, default context and default runtime configuration. It
 * is intentionally separate from a single execution. Per-run input and
 * overrides belong to {@link RunRequest}; mutable run state belongs to the
 * execution context created by the engine.
 * </p>
 *
 * <p>
 * Instances are usually created through {@link #builder(String)}.
 * </p>
 */
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
        this.defaultContext = defaultContext != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(defaultContext))
                : Map.of();
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    /**
     * Starts building a pipeline definition.
     *
     * @param id    stable pipeline identifier used in traces, generated root
     *              station ids and external references
     * @param <IN>  input type accepted by the first station
     * @param <OUT> output type produced by the current end of the pipeline
     * @return a new builder
     */
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

    /**
     * Fluent builder used by the Java DSL and generated pipeline definitions.
     *
     * <p>
     * Each call to {@link #then(AbstractStation)} appends one station to the
     * pipeline. Multiple stations are wrapped in a synthetic root
     * {@link SequenceStation}; a single station becomes the root directly.
     * </p>
     */
    public static class Builder<IN, OUT> {
        private final String id;
        private final List<AbstractStation<?, ?>> operations;
        private final Map<String, Object> defaultContext;
        private final Configuration.Builder configBuilder;
        private String version;

        private Builder(String id) {
            this.id = id;
            this.operations = new ArrayList<>();
            this.defaultContext = new LinkedHashMap<>();
            this.configBuilder = Configuration.builder();
        }

        private <PREVIOUS_OUT> Builder(Builder<IN, PREVIOUS_OUT> source) {
            this.id = source.id;
            this.operations = new ArrayList<>(source.operations);
            this.defaultContext = new LinkedHashMap<>(source.defaultContext);
            this.configBuilder = new Configuration.Builder(source.configBuilder);
            this.version = source.version;
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

        public Builder<IN, OUT> runtimeContract(AssemblyLineRuntimeContract runtimeContract) {
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

        /**
         * Appends a station and narrows the builder output type to the appended station
         * output type.
         */
        public <T> Builder<IN, T> then(AbstractStation<OUT, T> operation) {
            Objects.requireNonNull(operation);
            this.operations.add(operation);
            return new Builder<>(this);
        }

        public AssemblyLine<IN, OUT> build() {
            Objects.requireNonNull(id, "id");
            Configuration finalConfig = this.configBuilder.build();
            AbstractStation<?, ?> root;
            if (operations.isEmpty()) {
                root = SequenceStation.syntheticRoot(id + ":root", List.of(), FlowConfig.DEFAULT);
            } else if (operations.size() == 1) {
                root = operations.get(0);
            } else {
                root = SequenceStation.syntheticRoot(id + ":root", operations, FlowConfig.DEFAULT);
            }
            return new AssemblyLine<>(id, version, root, defaultContext, finalConfig);
        }
    }

    /**
     * Default runtime configuration attached to a pipeline definition.
     *
     * <p>
     * This configuration is part of the pipeline contract. Per-run overrides still
     * belong to {@link RunRequest}. If any runtime service is configured here, the
     * default runtime contract becomes nested-run-only unless explicitly set.
     * </p>
     */
    public static class Configuration {
        private final PersistenceConfiguration persistence;
        private final EventHandlingDefinition eventHandlingDefinition;
        private final List<RuntimeExtension> defaultExtensions;
        private final AssemblyLineRuntimeContract runtimeContract;

        private Configuration(PersistenceConfiguration persistence,
                              EventHandlingDefinition eventHandlingDefinition,
                              List<RuntimeExtension> defaultExtensions,
                              AssemblyLineRuntimeContract runtimeContract) {
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

        public AssemblyLineRuntimeContract getRuntimeContract() {
            return runtimeContract;
        }

        /**
         * Builder for pipeline-level runtime configuration.
         */
        public static class Builder {
            private final List<RuntimeExtension> defaultExtensions = new ArrayList<>();
            private PersistenceConfiguration persistence;
            private EventHandlingDefinition eventHandlingDefinition;
            private AssemblyLineRuntimeContract runtimeContract;

            public Builder() {
            }

            private Builder(Builder source) {
                this.persistence = source.persistence;
                this.eventHandlingDefinition = source.eventHandlingDefinition;
                this.defaultExtensions.addAll(source.defaultExtensions);
                this.runtimeContract = source.runtimeContract;
            }

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

            public Builder runtimeContract(AssemblyLineRuntimeContract runtimeContract) {
                this.runtimeContract = runtimeContract;
                return this;
            }

            public Configuration build() {
                AssemblyLineRuntimeContract finalRuntimeContract = runtimeContract != null ? runtimeContract
                        : defaultRuntimeContract();
                AssemblyLineRuntimeContractValidator.validateConfigurationCoherence(finalRuntimeContract, persistence,
                                                                                    eventHandlingDefinition,
                                                                                    defaultExtensions);
                return new Configuration(persistence, eventHandlingDefinition, defaultExtensions, finalRuntimeContract);
            }

            private AssemblyLineRuntimeContract defaultRuntimeContract() {
                boolean hasRuntimeConfiguration = persistence != null || eventHandlingDefinition != null
                        || !defaultExtensions.isEmpty();
                return hasRuntimeConfiguration ? AssemblyLineRuntimeContract.nestedRunOnly()
                        : AssemblyLineRuntimeContract.inlineConfigless();
            }
        }
    }
}

package io.github.gear4jtest.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AssemblyLine<IN, OUT> {

    private final String id;
    private final AbstractStation<?, ?> rootStation;
    private final Map<String, Object> defaultContext;
    private final Configuration configuration;

    private AssemblyLine(String id,
                         AbstractStation<?, ?> rootStation,
                         Map<String, Object> defaultContext,
                         Configuration configuration) {
        this.id = id;
        this.rootStation = Objects.requireNonNull(rootStation, "rootStation must not be null");
        this.defaultContext = defaultContext != null ? new HashMap<>(defaultContext) : new HashMap<>();
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    public String getId() {
        return id;
    }

    public AbstractStation<?, ?> getRootStation() {
        return rootStation;
    }

    public List<AbstractStation> getStations() {
        // Compatibilité : historiquement, une AL exposait une liste. Désormais, l'exécution s'appuie sur une racine unique.
        return Collections.unmodifiableList(List.of(this.rootStation));
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    public Map<String, Object> getDefaultContext() {
        return defaultContext;
    }

    public static <IN, OUT> Builder<IN, OUT> builder(String id) {
        return new Builder<>(id);
    }

    public static class Builder<IN, OUT> {
        private final String id;
        private final List<AbstractStation<?, ?>> operations = new ArrayList<>();
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
                root = SequenceStation.syntheticRoot(id + ":root", List.of(), io.github.gear4jtest.core.engine.flow.FlowConfig.DEFAULT);
            } else if (operations.size() == 1) {
                root = operations.get(0);
            } else {
                root = SequenceStation.syntheticRoot(id + ":root", operations, io.github.gear4jtest.core.engine.flow.FlowConfig.DEFAULT);
            }
            return new AssemblyLine<>(id, root, defaultContext, finalConfig);
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

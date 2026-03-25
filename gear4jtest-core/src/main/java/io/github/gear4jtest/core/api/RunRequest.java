package io.github.gear4jtest.core.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;
import io.github.gear4jtest.core.spi.factory.IdGenerator;

public class RunRequest {

    private final Object input;
    private final Map<String, Object> context;
    private final ResourceFactory resourceFactory;
    private final List<RuntimeExtension> extensions;
    private final IdGenerator idGenerator;

    private RunRequest(Builder builder) {
        this.input = builder.input;
        this.context = builder.context;
        this.resourceFactory = builder.resourceFactory;
        this.extensions = List.copyOf(builder.extensions);
        this.idGenerator = builder.idGenerator;
    }

    public static Builder builder() {
        return new Builder();
    }

//    /**
//     * Récupère une feature par sa classe.
//     * Utilisé par les Extensions pour récupérer leur configuration spécifique.
//     */
//    public <T extends PipelineFeature> Optional<T> getFeature(Class<T> featureClass) {
//        return extensions.values().stream()
//                .filter(featureClass::isInstance)
//                .map(featureClass::cast)
//                .findFirst();
//    }

//    public boolean hasFeature(String key) {
//        return extensions.containsKey(key);
//    }
//
//    public Collection<PipelineFeature> getActiveFeatures() {
//        return extensions.values();
//    }

    public List<RuntimeExtension> getExtensions() {
        return Collections.unmodifiableList(extensions);
    }

    public Object getInput() {
        return input;
    }

    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public IdGenerator getIdGenerator() {
        return idGenerator;
    }

    public static class Builder {
        private final List<RuntimeExtension> extensions = new ArrayList<>();
        private Object input;
        private Map<String, Object> context;
        private ResourceFactory resourceFactory;
        private IdGenerator idGenerator;

        public Builder input(Object input) {
            this.input = input;
            return this;
        }

        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public Builder resourceFactory(ResourceFactory resourceFactory) {
            this.resourceFactory = resourceFactory;
            return this;
        }

        public Builder withIdGenerator(IdGenerator idGenerator) {
            this.idGenerator = idGenerator;
            return this;
        }

        /**
         * Active une feature.
         * Ex: .with(new PersistenceFeature(myDbManager))
         */
        public Builder with(RuntimeExtension extension) {
            Objects.requireNonNull(extension);
            this.extensions.add(extension);
            return this;
        }

        public RunRequest build() {
            return new RunRequest(this);
        }
    }
}
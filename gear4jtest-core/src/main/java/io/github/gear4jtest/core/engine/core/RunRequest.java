package io.github.gear4jtest.core.engine.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.core.engine.spi.RuntimeExtension;
import io.github.gear4jtest.core.factory.ResourceFactory;

public class RunRequest {

    private final Object input;
    private final ContextInfo contextInfo;
    private final Map<String, Object> context;
    private final ResourceFactory resourceFactory;
    private final List<RuntimeExtension> extensions;

    private RunRequest(Builder builder) {
        this.input = builder.input;
        this.contextInfo = builder.contextInfo != null ? builder.contextInfo : ContextInfo.anonymous();
        this.context = builder.context;
        this.resourceFactory = builder.resourceFactory;
        this.extensions = Collections.unmodifiableList(builder.extensions);
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

    public ContextInfo getContextInfo() {
        return contextInfo;
    }

    public ResourceFactory getResourceFactory() {
        return resourceFactory;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public static class Builder {
        private final List<RuntimeExtension> extensions = new ArrayList<>();
        private Object input;
        private ContextInfo contextInfo;
        private Map<String, Object> context;
        private ResourceFactory resourceFactory;

        public Builder input(Object input) {
            this.input = input;
            return this;
        }

        public Builder contextInfo(ContextInfo info) {
            this.contextInfo = info;
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
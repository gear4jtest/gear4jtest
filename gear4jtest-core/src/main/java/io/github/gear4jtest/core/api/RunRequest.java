package io.github.gear4jtest.core.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.core.api.context.CancellationToken;
import io.github.gear4jtest.core.api.pipeline.NestedRunContext;
import io.github.gear4jtest.core.api.pipeline.PipelineCallStack;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.factory.IdGenerator;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;

/**
 * Per-execution input and runtime overrides.
 *
 * <p>
 * A request carries the user input, additional context values and optional
 * services/extensions that are specific to one run. Pipeline-level defaults
 * remain on {@link AssemblyLine}; request values are merged by the engine when
 * the run starts.
 * </p>
 */
public class RunRequest {
    private final Object input;
    private final Map<String, Object> context;
    private final ResourceFactory resourceFactory;
    private final List<RuntimeExtension> extensions;
    private final IdGenerator idGenerator;
    private final NestedRunContext nestedRunContext;
    private final PipelineCallStack pipelineCallStack;
    private final CancellationToken cancellationToken;

    private RunRequest(Builder builder) {
        this.input = builder.input;
        this.context = builder.context == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.context));
        this.resourceFactory = builder.resourceFactory;
        this.extensions = List.copyOf(builder.extensions);
        this.idGenerator = builder.idGenerator;
        this.nestedRunContext = builder.nestedRunContext;
        this.pipelineCallStack = builder.pipelineCallStack;
        this.cancellationToken = builder.cancellationToken;
    }

    public static Builder builder() {
        return new Builder();
    }

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

    public NestedRunContext getNestedRunContext() {
        return nestedRunContext;
    }

    public PipelineCallStack getPipelineCallStack() {
        return pipelineCallStack;
    }

    public CancellationToken getCancellationToken() {
        return cancellationToken;
    }

    /**
     * Creates a builder initialized with the current request values.
     */
    public Builder toBuilder() {
        Builder builder = new Builder()
                .input(input)
                .context(context)
                .resourceFactory(resourceFactory)
                .withIdGenerator(idGenerator)
                .nestedRunContext(nestedRunContext)
                .pipelineCallStack(pipelineCallStack)
                .cancellationToken(cancellationToken);
        extensions.forEach(builder::with);
        return builder;
    }

    /**
     * Builder for per-run request values.
     */
    public static class Builder {
        private final List<RuntimeExtension> extensions = new ArrayList<>();
        private Object input;
        private Map<String, Object> context;
        private ResourceFactory resourceFactory;
        private IdGenerator idGenerator;
        private NestedRunContext nestedRunContext;
        private PipelineCallStack pipelineCallStack;
        private CancellationToken cancellationToken;

        public Builder input(Object input) {
            this.input = input;
            return this;
        }

        public Builder context(Map<String, Object> context) {
            this.context = context == null ? null : new LinkedHashMap<>(context);
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

        public Builder nestedRunContext(NestedRunContext nestedRunContext) {
            this.nestedRunContext = nestedRunContext;
            return this;
        }

        public Builder pipelineCallStack(PipelineCallStack pipelineCallStack) {
            this.pipelineCallStack = pipelineCallStack;
            return this;
        }

        /**
         * Supplies a token that allows callers and long-running operators to cooperate
         * on cancellation.
         */
        public Builder cancellationToken(CancellationToken cancellationToken) {
            this.cancellationToken = cancellationToken;
            return this;
        }

        /**
         * Adds a run-scoped extension.
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

package io.github.gear4jtest.core.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.core.api.assemblyline.AssemblyLineCallStack;
import io.github.gear4jtest.core.api.assemblyline.NestedRunContext;
import io.github.gear4jtest.core.api.context.CancellationToken;
import io.github.gear4jtest.core.spi.extension.RuntimeExtension;
import io.github.gear4jtest.core.spi.factory.IdGenerator;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;

/**
 * Per-execution input and runtime overrides.
 *
 * <p>
 * A request carries the user input, additional context values and optional
 * services/extensions that are specific to one run. AssemblyLine-level defaults
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
    private final AssemblyLineCallStack assemblyLineCallStack;
    private final CancellationToken cancellationToken;

    private RunRequest(Builder builder) {
        this.input = builder.input;
        this.context = builder.context == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.context));
        this.resourceFactory = builder.resourceFactory;
        this.extensions = List.copyOf(builder.extensions);
        this.idGenerator = builder.idGenerator;
        this.nestedRunContext = builder.nestedRunContext;
        this.assemblyLineCallStack = builder.assemblyLineCallStack;
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

    public AssemblyLineCallStack getAssemblyLineCallStack() {
        return assemblyLineCallStack;
    }

    public CancellationToken getCancellationToken() {
        return cancellationToken;
    }

    /**
     * Creates a builder initialized with the current request values.
     *
     * <p>
     * This method preserves all runtime objects, including the
     * {@link CancellationToken} and the {@link AssemblyLineCallStack}. That is the
     * right behavior when deriving a request that must remain part of the same
     * cancellation/call-stack scope, for example internal nested-run propagation.
     * For an independent top-level run, prefer {@link #toIndependentBuilder()} or
     * replace those fields explicitly before building the copy.
     * </p>
     */
    public Builder toBuilder() {
        Builder builder = copyIntoBuilder()
                .nestedRunContext(nestedRunContext)
                .assemblyLineCallStack(assemblyLineCallStack)
                .cancellationToken(cancellationToken);
        extensions.forEach(builder::with);
        return builder;
    }

    /**
     * Creates a builder initialized with the reusable request values, but without
     * sharing cancellation or call-stack state with the source request.
     *
     * <p>
     * Use this helper when a request acts as a template for multiple independent
     * top-level runs. The new request keeps input, context, resource factory, id
     * generator and extensions, but drops nested-run metadata and lets the engine
     * allocate a fresh {@link CancellationToken} and {@link AssemblyLineCallStack}
     * unless the caller explicitly supplies them again on the returned builder.
     * </p>
     */
    public Builder toIndependentBuilder() {
        Builder builder = copyIntoBuilder();
        extensions.forEach(builder::with);
        return builder;
    }

    private Builder copyIntoBuilder() {
        return new Builder()
                .input(input)
                .context(context)
                .resourceFactory(resourceFactory)
                .withIdGenerator(idGenerator);
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
        private AssemblyLineCallStack assemblyLineCallStack;
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

        public Builder assemblyLineCallStack(AssemblyLineCallStack assemblyLineCallStack) {
            this.assemblyLineCallStack = assemblyLineCallStack;
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

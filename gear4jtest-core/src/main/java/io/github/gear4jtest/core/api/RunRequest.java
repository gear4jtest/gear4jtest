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
public class RunRequest<IN> {
    private final IN input;
    private final Map<String, Object> context;
    private final ResourceFactory resourceFactory;
    private final List<RuntimeExtension> extensions;
    private final IdGenerator idGenerator;
    private final NestedRunContext nestedRunContext;
    private final AssemblyLineCallStack assemblyLineCallStack;
    private final CancellationToken cancellationToken;

    private RunRequest(Builder<IN> builder) {
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

    public static <IN> Builder<IN> builder() {
        return new Builder<>();
    }

    public List<RuntimeExtension> getExtensions() {
        return Collections.unmodifiableList(extensions);
    }

    public IN getInput() {
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
    public Builder<IN> toBuilder() {
        Builder<IN> builder = copyIntoBuilder()
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
    public Builder<IN> toIndependentBuilder() {
        Builder<IN> builder = copyIntoBuilder();
        extensions.forEach(builder::with);
        return builder;
    }

    private Builder<IN> copyIntoBuilder() {
        return new Builder<IN>()
                .input(input)
                .context(context)
                .resourceFactory(resourceFactory)
                .withIdGenerator(idGenerator);
    }

    /**
     * Builder for per-run request values.
     */
    public static class Builder<IN> {
        private final List<RuntimeExtension> extensions = new ArrayList<>();
        private IN input;
        private Map<String, Object> context;
        private ResourceFactory resourceFactory;
        private IdGenerator idGenerator;
        private NestedRunContext nestedRunContext;
        private AssemblyLineCallStack assemblyLineCallStack;
        private CancellationToken cancellationToken;

        /**
         * Sets the request input and narrows the builder type to the concrete input
         * type used by this call.
         *
         * <p>
         * The bounded type parameter preserves the convenient
         * {@code RunRequest.builder().input(value).build()} form while preventing a
         * builder whose input type is already specific from being widened to an
         * unrelated type.
         * </p>
         */
        @SuppressWarnings("unchecked")
        public <NEW_IN extends IN> Builder<NEW_IN> input(NEW_IN input) {
            this.input = input;
            return (Builder<NEW_IN>) this;
        }

        public Builder<IN> context(Map<String, Object> context) {
            this.context = context == null ? null : new LinkedHashMap<>(context);
            return this;
        }

        public Builder<IN> resourceFactory(ResourceFactory resourceFactory) {
            this.resourceFactory = resourceFactory;
            return this;
        }

        public Builder<IN> withIdGenerator(IdGenerator idGenerator) {
            this.idGenerator = idGenerator;
            return this;
        }

        public Builder<IN> nestedRunContext(NestedRunContext nestedRunContext) {
            this.nestedRunContext = nestedRunContext;
            return this;
        }

        public Builder<IN> assemblyLineCallStack(AssemblyLineCallStack assemblyLineCallStack) {
            this.assemblyLineCallStack = assemblyLineCallStack;
            return this;
        }

        /**
         * Supplies a token that allows callers and long-running operators to cooperate
         * on cancellation.
         */
        public Builder<IN> cancellationToken(CancellationToken cancellationToken) {
            this.cancellationToken = cancellationToken;
            return this;
        }

        /**
         * Adds a run-scoped extension.
         */
        public Builder<IN> with(RuntimeExtension extension) {
            Objects.requireNonNull(extension);
            this.extensions.add(extension);
            return this;
        }

        public RunRequest<IN> build() {
            return new RunRequest<>(this);
        }
    }
}

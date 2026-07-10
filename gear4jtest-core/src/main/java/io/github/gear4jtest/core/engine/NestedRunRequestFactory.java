package io.github.gear4jtest.core.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.assemblyline.NestedRunContext;
import io.github.gear4jtest.core.api.context.ContextPropagationPolicy;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.spi.factory.IdGenerator;

final class NestedRunRequestFactory {
    private NestedRunRequestFactory() {
    }

    static <IN> RunRequest<IN> create(IN input,
                                      StationExecutionContext parentContext,
                                      IdGenerator defaultIdGenerator,
                                      ContextPropagationPolicy contextPropagationPolicy) {
        NestedRunContext nestedRunContext = NestedRunContext.from(parentContext);

        Map<String, Object> propagatedContext = propagatedContext(parentContext, contextPropagationPolicy);
        return RunRequest.<IN>builder().input(input)
                .context(propagatedContext)
                .resourceFactory(parentContext.getServices().getResourceFactory())
                .withIdGenerator(Optional.ofNullable(parentContext.getGlobalContext().getIdGenerator())
                        .orElse(defaultIdGenerator))
                .nestedRunContext(nestedRunContext)
                .assemblyLineCallStack(parentContext.getGlobalContext().getAssemblyLineCallStack())
                .cancellationToken(parentContext.getGlobalContext().getCancellationToken()).build();
    }

    private static Map<String, Object> propagatedContext(StationExecutionContext parentContext,
                                                         ContextPropagationPolicy contextPropagationPolicy) {
        ContextPropagationPolicy effectivePolicy = contextPropagationPolicy != null ? contextPropagationPolicy
                : ContextPropagationPolicy.inheritAllShallow();
        Map<String, Object> parentSnapshot = parentContext.getGlobalContext().snapshotContext();
        Map<String, Object> propagated = effectivePolicy.propagate(parentSnapshot);
        if (propagated == null || propagated.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : propagated.entrySet()) {
            if (entry.getKey() == null) {
                throw new IllegalArgumentException("Nested run context propagation produced a null key");
            }
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("Nested run context propagation produced a null value for key '"
                        + entry.getKey() + "'");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }
}

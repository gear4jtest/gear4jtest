package io.github.gear4jtest.core.engine;

import java.util.HashMap;
import java.util.Optional;

import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.assemblyline.NestedRunContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.spi.factory.IdGenerator;

final class NestedRunRequestFactory {
    private NestedRunRequestFactory() {
    }

    static RunRequest create(Object input, StationExecutionContext parentContext, IdGenerator defaultIdGenerator) {
        NestedRunContext nestedRunContext = NestedRunContext.from(parentContext);

        /*
         * NESTED_RUN currently inherits the full key/value context from the parent run.
         * This is an explicit MVP choice. A future ContextPropagationPolicy can narrow
         * this to NONE, ALL or an explicit projection without changing the
         * AssemblyLineCallStation contract.
         */
        return RunRequest.builder().input(input)
                .context(new HashMap<>(parentContext.getGlobalContext().getContext()))
                .resourceFactory(parentContext.getServices().getResourceFactory())
                .withIdGenerator(Optional.ofNullable(parentContext.getGlobalContext().getIdGenerator())
                        .orElse(defaultIdGenerator))
                .nestedRunContext(nestedRunContext)
                .assemblyLineCallStack(parentContext.getGlobalContext().getAssemblyLineCallStack())
                .cancellationToken(parentContext.getGlobalContext().getCancellationToken()).build();
    }
}

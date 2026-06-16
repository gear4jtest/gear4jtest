package io.github.gear4jtest.core.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationScopedResourceRegistry;
import io.github.gear4jtest.core.api.pipeline.NestedRunContext;
import io.github.gear4jtest.core.api.pipeline.PipelineCallStack;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.spi.factory.IdGenerator;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;

final class PipelineExecutionContextFactory {
    private final ResourceFactory resourceFactory;
    private final ExecutionContextRegistry executionContextRegistry;
    private final IdGenerator defaultIdGenerator;

    PipelineExecutionContextFactory(ResourceFactory resourceFactory,
                                    ExecutionContextRegistry executionContextRegistry,
                                    IdGenerator defaultIdGenerator) {
        this.resourceFactory = resourceFactory;
        this.executionContextRegistry = executionContextRegistry;
        this.defaultIdGenerator = defaultIdGenerator;
    }

    <IN, OUT> PipelineRunContext create(AssemblyLine<IN, OUT> pipeline,
                                        RunRequest request,
                                        PipelineCallStack callStack,
                                        EventHandlingDefinition eventHandlingDefinition,
                                        EventManager eventManager) {
        Map<String, Object> effectiveContext = new HashMap<>(pipeline.getDefaultContext());
        if (request.getContext() != null) {
            effectiveContext.putAll(request.getContext());
        }

        ExecutionContext.EventRuntimeOptions eventRuntimeOptions = ExecutionContext.EventRuntimeOptions
                .from(eventHandlingDefinition);
        IdGenerator effectiveGenerator = Optional.ofNullable(request.getIdGenerator()).orElse(defaultIdGenerator);

        var executionId = effectiveGenerator.generate();
        var execution = new AssemblyRunTrace(executionId, pipeline.getId(), new HashMap<>(effectiveContext));
        applyNestedRunContext(execution, request.getNestedRunContext());

        var effectiveResourceFactory = Optional.ofNullable(request.getResourceFactory())
                .or(() -> Optional.ofNullable(resourceFactory)).orElseThrow();

        ExecutionServices services = new ExecutionServices(eventManager, effectiveResourceFactory,
                new StationScopedResourceRegistry());

        var context = new ExecutionContext(executionId, pipeline.getId(), services, execution, eventRuntimeOptions,
                pipeline.getConfiguration().getRuntimeContract(), callStack, effectiveGenerator,
                request.getCancellationToken());
        context.getContext().putAll(effectiveContext);

        executionContextRegistry.register(context);
        return new PipelineRunContext(context, execution, effectiveContext, effectiveGenerator);
    }

    private static void applyNestedRunContext(AssemblyRunTrace execution, NestedRunContext nestedRunContext) {
        if (nestedRunContext == null) {
            return;
        }
        execution.setParentExecutionId(nestedRunContext.parentExecutionId());
        execution.setRootExecutionId(nestedRunContext.rootExecutionId());
        execution.setParentStationLogId(nestedRunContext.parentStationLogId());
    }
}

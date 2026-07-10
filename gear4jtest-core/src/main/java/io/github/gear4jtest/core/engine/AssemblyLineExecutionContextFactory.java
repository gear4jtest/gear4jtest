package io.github.gear4jtest.core.engine;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.RunRequest;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineCallStack;
import io.github.gear4jtest.core.api.assemblyline.NestedRunContext;
import io.github.gear4jtest.core.api.config.EventHandlingDefinition;
import io.github.gear4jtest.core.api.context.ContextPropagationPolicy;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.ExecutionServices;
import io.github.gear4jtest.core.api.context.StationScopedResourceRegistry;
import io.github.gear4jtest.core.event.EventManager;
import io.github.gear4jtest.core.execution.ExecutionContextRegistry;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.spi.factory.IdGenerator;
import io.github.gear4jtest.core.spi.factory.ResourceFactory;

final class AssemblyLineExecutionContextFactory {
    private final ResourceFactory resourceFactory;
    private final ExecutionContextRegistry executionContextRegistry;
    private final IdGenerator defaultIdGenerator;

    AssemblyLineExecutionContextFactory(ResourceFactory resourceFactory,
                                        ExecutionContextRegistry executionContextRegistry,
                                        IdGenerator defaultIdGenerator) {
        this.resourceFactory = resourceFactory;
        this.executionContextRegistry = executionContextRegistry;
        this.defaultIdGenerator = defaultIdGenerator;
    }

    <IN, OUT> AssemblyLineRunContext create(AssemblyLine<IN, OUT> pipeline,
                                            RunRequest<IN> request,
                                            AssemblyLineCallStack callStack,
                                            EventHandlingDefinition eventHandlingDefinition,
                                            EventManager eventManager,
                                            ContextPropagationPolicy initialRunContextPolicy) {
        Map<String, Object> mergedContext = new LinkedHashMap<>(pipeline.getDefaultContext());
        if (request.getContext() != null) {
            mergedContext.putAll(request.getContext());
        }
        Map<String, Object> effectiveContext = isolateInitialContext(mergedContext, initialRunContextPolicy);

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

        var context = ExecutionContext.builder()
                .executionId(executionId)
                .assemblyLineId(pipeline.getId())
                .services(services)
                .assemblyRun(execution)
                .eventRuntimeOptions(eventRuntimeOptions)
                .runtimeContract(pipeline.getConfiguration().getRuntimeContract())
                .assemblyLineCallStack(callStack)
                .idGenerator(effectiveGenerator)
                .cancellationToken(request.getCancellationToken())
                .build();
        context.getContext().putAll(effectiveContext);

        executionContextRegistry.register(context);
        return new AssemblyLineRunContext(context, execution, effectiveContext, effectiveGenerator);
    }

    private static Map<String, Object> isolateInitialContext(Map<String, Object> mergedContext,
                                                             ContextPropagationPolicy initialRunContextPolicy) {
        ContextPropagationPolicy policy = initialRunContextPolicy != null ? initialRunContextPolicy
                : ContextPropagationPolicy.inheritAllShallow();
        Map<String, Object> source = Collections.unmodifiableMap(new LinkedHashMap<>(mergedContext));
        Map<String, Object> isolated = policy.propagate(source);
        if (isolated == null || isolated.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new HashMap<>(isolated);
        copy.forEach((key, value) -> {
            Objects.requireNonNull(key, "run context key must not be null");
            Objects.requireNonNull(value, "run context value must not be null for key=" + key);
        });
        return copy;
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

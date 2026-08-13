package io.github.gear4jtest.core.engine.strategy;

import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineCallStack;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineExecutionMode;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineReference;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineRuntimeContractValidator;
import io.github.gear4jtest.core.api.assemblyline.AssemblyLineTarget;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.station.AssemblyLineCallStation;
import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.engine.context.EngineStationContexts;
import io.github.gear4jtest.core.exception.AssemblyLineCallException;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class AssemblyLineCallStationStrategy extends AbstractStationStrategy<AssemblyLineCallStation<?, ?>> {
    private static final String CONTEXT_PREFIX = "assemblyLine.call.";
    private final NestedAssemblyLineExecutor nestedAssemblyLineExecutor;

    public AssemblyLineCallStationStrategy(NestedAssemblyLineExecutor nestedAssemblyLineExecutor) {
        this.nestedAssemblyLineExecutor = Objects.requireNonNull(nestedAssemblyLineExecutor,
                                                                 "nestedAssemblyLineExecutor must not be null");
    }

    @Override
    public boolean supports(Class<?> type) {
        return AssemblyLineCallStation.class.isAssignableFrom(type);
    }

    @Override
    public Object doExecute(AssemblyLineCallStation<?, ?> station,
                            Object input,
                            StationRunner runner,
                            StationExecutionContext operationExecution) {
        AssemblyLine<?, ?> childAssemblyLine = resolveAssemblyLine(station);
        writeTargetMetadata(station, childAssemblyLine, EngineStationContexts.trace(operationExecution));

        if (station.getExecutionMode() == AssemblyLineExecutionMode.INLINE) {
            return executeInline(station, input, runner, operationExecution, childAssemblyLine);
        }
        return executeNested(station, input, operationExecution, childAssemblyLine);
    }

    private Object executeInline(AssemblyLineCallStation<?, ?> station,
                                 Object input,
                                 StationRunner runner,
                                 StationExecutionContext operationExecution,
                                 AssemblyLine<?, ?> childAssemblyLine) {
        AssemblyLineRuntimeContractValidator
                .validateInlineAllowed(childAssemblyLine, operationExecution.getGlobalContext().getRuntimeContract());

        AssemblyLineReference childReference = targetReference(station.getTarget(), childAssemblyLine);
        try (AssemblyLineCallStack.Scope ignored = operationExecution.getGlobalContext().getAssemblyLineCallStack()
                .enter(childReference)) {
            StationLogTrace childRootLog = EngineStationContexts.mutableTrace(
                                                                              runner.run(input,
                                                                                         childAssemblyLine
                                                                                                 .getRootStation(),
                                                                                         operationExecution));
            return mapInlineChildStatus(station, childAssemblyLine, childRootLog,
                                        EngineStationContexts.trace(operationExecution));
        }
    }

    private Object executeNested(AssemblyLineCallStation<?, ?> station,
                                 Object input,
                                 StationExecutionContext operationExecution,
                                 AssemblyLine<?, ?> childAssemblyLine) {
        ExecutionResult<?> childResult = nestedAssemblyLineExecutor.executeNested(station, childAssemblyLine, input,
                                                                                  operationExecution);
        RunTrace childExecution = childResult.getExecution();
        if (childExecution != null) {
            EngineStationContexts.trace(operationExecution).mutableContext().put(CONTEXT_PREFIX + "childExecutionId",
                                                                                 childExecution.getId());
        }
        return mapNestedChildStatus(station, childAssemblyLine, childResult,
                                    EngineStationContexts.trace(operationExecution));
    }

    private Object mapInlineChildStatus(AssemblyLineCallStation<?, ?> station,
                                        AssemblyLine<?, ?> childAssemblyLine,
                                        StationLogTrace childRootLog,
                                        StationLogTrace callLog) {
        Object childOutput = childRootLog.getOutput();
        return switch (childRootLog.getStatus()) {
            case SUCCEEDED -> childOutput;
            case SKIPPED -> {
                callLog.markSkipped("Child assembly line root was skipped: " + childAssemblyLine.getId());
                callLog.setOutput(childOutput);
                yield childOutput;
            }
            case STOPPED -> {
                Exception exception = representativeException(childRootLog, "Inline child assembly line stopped: "
                        + childAssemblyLine.getId());
                callLog.markStopped(exception);
                callLog.setOutput(childOutput);
                yield childOutput;
            }
            case CANCELLED -> {
                Exception exception = representativeException(childRootLog, "Inline child assembly line cancelled: "
                        + childAssemblyLine.getId());
                callLog.markCancelled(exception);
                callLog.setOutput(childOutput);
                yield childOutput;
            }
            case FAILED,
                    RUNNING ->
                throw new AssemblyLineCallException(
                        "Inline child assembly line '" + childAssemblyLine.getId() + ":"
                                + childAssemblyLine.getVersion()
                                + "' failed in station '" + station.getId() + "'",
                        representativeException(childRootLog, "Child assembly line failed"));
        };
    }

    private Object mapNestedChildStatus(AssemblyLineCallStation<?, ?> station,
                                        AssemblyLine<?, ?> childAssemblyLine,
                                        ExecutionResult<?> childResult,
                                        StationLogTrace callLog) {
        RunTrace childExecution = childResult.getExecution();
        ExecutionStatus childStatus = childExecution != null ? childExecution.getStatus() : null;
        Object childOutput = childResult.getResult();

        if (childStatus == ExecutionStatus.SUCCEEDED) {
            return childOutput;
        }
        if (childStatus == ExecutionStatus.SKIPPED) {
            callLog.markSkipped("Nested child assembly line was skipped: " + childAssemblyLine.getId());
            callLog.setOutput(childOutput);
            return childOutput;
        }
        if (childStatus == ExecutionStatus.STOPPED) {
            callLog.markStopped(representativeException(childResult,
                                                        "Nested child assembly line stopped: "
                                                                + childAssemblyLine.getId()));
            callLog.setOutput(childOutput);
            return childOutput;
        }
        if (childStatus == ExecutionStatus.CANCELLED) {
            callLog.markCancelled(representativeException(childResult,
                                                          "Nested child assembly line cancelled: "
                                                                  + childAssemblyLine.getId()));
            callLog.setOutput(childOutput);
            return childOutput;
        }

        throw new AssemblyLineCallException(
                "Nested child assembly line '" + childAssemblyLine.getId() + ":" + childAssemblyLine.getVersion()
                        + "' failed in station '" + station.getId() + "'",
                representativeException(childResult, "Nested child assembly line failed"));
    }

    private AssemblyLine<?, ?> resolveAssemblyLine(AssemblyLineCallStation<?, ?> station) {
        return station.getTarget().getResolvedAssemblyLine()
                .orElseThrow(() -> new AssemblyLineCallException(
                        "AssemblyLine target '" + station.getTarget().declaredReference().displayName()
                                + "' is not resolved. Resolve declarative references before execution."));
    }

    private AssemblyLineReference targetReference(AssemblyLineTarget<?, ?> target,
                                                  AssemblyLine<?, ?> fallbackAssemblyLine) {
        return target.getResolvedReference().orElseGet(() -> AssemblyLineReference.from(fallbackAssemblyLine));
    }

    private void writeTargetMetadata(AssemblyLineCallStation<?, ?> station,
                                     AssemblyLine<?, ?> childAssemblyLine,
                                     StationLogTrace callLog) {
        Map<String, Object> context = callLog.mutableContext();
        context.put(CONTEXT_PREFIX + "mode", station.getExecutionMode().name());
        context.put(CONTEXT_PREFIX + "declaredReference", station.getTarget().declaredReference().displayName());
        context.put(CONTEXT_PREFIX + "resolvedReference",
                    targetReference(station.getTarget(), childAssemblyLine).displayName());
    }

    private Exception representativeException(StationLogTrace log, String fallbackMessage) {
        if (log.getThrowables() != null && !log.getThrowables().isEmpty()) {
            Throwable first = log.getThrowables().get(0);
            if (first instanceof Exception exception) {
                return exception;
            }
            return new RuntimeException(first.getMessage(), first);
        }
        String message = log.getErrorMessage() != null ? log.getErrorMessage() : fallbackMessage;
        return new RuntimeException(message);
    }

    private Exception representativeException(ExecutionResult<?> result, String fallbackMessage) {
        if (result.getError() != null) {
            return result.getError();
        }
        RunTrace execution = result.getExecution();
        String message = execution != null && execution.getErrorMessage() != null ? execution.getErrorMessage()
                : fallbackMessage;
        return new RuntimeException(message);
    }
}

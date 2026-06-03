package io.github.gear4jtest.core.engine.strategy;

import java.util.Map;
import java.util.Objects;

import io.github.gear4jtest.core.api.AssemblyLine;
import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.pipeline.PipelineCallStack;
import io.github.gear4jtest.core.api.pipeline.PipelineExecutionMode;
import io.github.gear4jtest.core.api.pipeline.PipelineReference;
import io.github.gear4jtest.core.api.pipeline.PipelineRuntimeContractValidator;
import io.github.gear4jtest.core.api.pipeline.PipelineTarget;
import io.github.gear4jtest.core.api.station.AbstractStation;
import io.github.gear4jtest.core.api.station.PipelineCallStation;
import io.github.gear4jtest.core.exception.PipelineCallException;
import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.execution.trace.StationLogTrace;
import io.github.gear4jtest.core.persistence.ExecutionStatus;
import io.github.gear4jtest.core.spi.runner.StationRunner;

public class PipelineCallStationStrategy extends AbstractStationStrategy<PipelineCallStation<?, ?>> {
    private static final String CONTEXT_PREFIX = "pipeline.call.";
    private final NestedPipelineExecutor nestedPipelineExecutor;

    public PipelineCallStationStrategy(NestedPipelineExecutor nestedPipelineExecutor) {
        this.nestedPipelineExecutor = Objects.requireNonNull(nestedPipelineExecutor,
                                                             "nestedPipelineExecutor must not be null");
    }

    @Override
    public boolean supports(Class<? extends AbstractStation<?, ?>> type) {
        return PipelineCallStation.class.isAssignableFrom(type);
    }

    @Override
    public Object doExecute(PipelineCallStation<?, ?> station,
                            Object input,
                            StationRunner runner,
                            StationExecutionContext operationExecution) {
        AssemblyLine<?, ?> childPipeline = resolvePipeline(station);
        writeTargetMetadata(station, childPipeline, operationExecution.getRecord());

        if (station.getExecutionMode() == PipelineExecutionMode.INLINE) {
            return executeInline(station, input, runner, operationExecution, childPipeline);
        }
        return executeNested(station, input, operationExecution, childPipeline);
    }

    private Object executeInline(PipelineCallStation<?, ?> station,
                                 Object input,
                                 StationRunner runner,
                                 StationExecutionContext operationExecution,
                                 AssemblyLine<?, ?> childPipeline) {
        PipelineRuntimeContractValidator
                .validateInlineAllowed(childPipeline, operationExecution.getGlobalContext().getRuntimeContract());

        PipelineReference childReference = targetReference(station.getTarget(), childPipeline);
        try (PipelineCallStack.Scope ignored = operationExecution.getGlobalContext().getPipelineCallStack()
                .enter(childReference)) {
            StationLogTrace childRootLog = runner.run(input, childPipeline.getRootStation(), operationExecution);
            return mapInlineChildStatus(station, childPipeline, childRootLog, operationExecution.getRecord());
        }
    }

    private Object executeNested(PipelineCallStation<?, ?> station,
                                 Object input,
                                 StationExecutionContext operationExecution,
                                 AssemblyLine<?, ?> childPipeline) {
        ExecutionResult<?> childResult = nestedPipelineExecutor.executeNested(station, childPipeline, input,
                                                                              operationExecution);
        AssemblyRunTrace childExecution = childResult.getExecution();
        if (childExecution != null) {
            operationExecution.getRecord().getContext().put(CONTEXT_PREFIX + "childExecutionId",
                                                            childExecution.getId());
        }
        return mapNestedChildStatus(station, childPipeline, childResult, operationExecution.getRecord());
    }

    private Object mapInlineChildStatus(PipelineCallStation<?, ?> station,
                                        AssemblyLine<?, ?> childPipeline,
                                        StationLogTrace childRootLog,
                                        StationLogTrace callLog) {
        Object childOutput = childRootLog.getOutput();
        return switch (childRootLog.getStatus()) {
            case SUCCEEDED -> childOutput;
            case SKIPPED -> {
                callLog.markSkipped("Child pipeline root was skipped: " + childPipeline.getId());
                callLog.setOutput(childOutput);
                yield childOutput;
            }
            case STOPPED -> {
                Exception exception = representativeException(childRootLog, "Inline child pipeline stopped: "
                        + childPipeline.getId());
                callLog.markStopped(exception);
                callLog.setOutput(childOutput);
                yield childOutput;
            }
            case CANCELLED -> {
                Exception exception = representativeException(childRootLog, "Inline child pipeline cancelled: "
                        + childPipeline.getId());
                callLog.markCancelled(exception);
                callLog.setOutput(childOutput);
                yield childOutput;
            }
            case FAILED,
                    RUNNING ->
                throw new PipelineCallException(
                        "Inline child pipeline '" + childPipeline.getId() + ":" + childPipeline.getVersion()
                                + "' failed in station '" + station.getId() + "'",
                        representativeException(childRootLog, "Child pipeline failed"));
        };
    }

    private Object mapNestedChildStatus(PipelineCallStation<?, ?> station,
                                        AssemblyLine<?, ?> childPipeline,
                                        ExecutionResult<?> childResult,
                                        StationLogTrace callLog) {
        AssemblyRunTrace childExecution = childResult.getExecution();
        ExecutionStatus childStatus = childExecution != null ? childExecution.getStatus() : null;
        Object childOutput = childResult.getResult();

        if (childStatus == ExecutionStatus.SUCCEEDED) {
            return childOutput;
        }
        if (childStatus == ExecutionStatus.SKIPPED) {
            callLog.markSkipped("Nested child pipeline was skipped: " + childPipeline.getId());
            callLog.setOutput(childOutput);
            return childOutput;
        }
        if (childStatus == ExecutionStatus.STOPPED) {
            callLog.markStopped(representativeException(childResult,
                                                        "Nested child pipeline stopped: " + childPipeline.getId()));
            callLog.setOutput(childOutput);
            return childOutput;
        }
        if (childStatus == ExecutionStatus.CANCELLED) {
            callLog.markCancelled(representativeException(childResult,
                                                          "Nested child pipeline cancelled: " + childPipeline.getId()));
            callLog.setOutput(childOutput);
            return childOutput;
        }

        throw new PipelineCallException(
                "Nested child pipeline '" + childPipeline.getId() + ":" + childPipeline.getVersion()
                        + "' failed in station '" + station.getId() + "'",
                representativeException(childResult, "Nested child pipeline failed"));
    }

    private AssemblyLine<?, ?> resolvePipeline(PipelineCallStation<?, ?> station) {
        return station.getTarget().getResolvedPipeline()
                .orElseThrow(() -> new PipelineCallException(
                        "Pipeline target '" + station.getTarget().declaredReference().displayName()
                                + "' is not resolved. Resolve declarative references before execution."));
    }

    private PipelineReference targetReference(PipelineTarget<?, ?> target, AssemblyLine<?, ?> fallbackPipeline) {
        return target.getResolvedReference().orElseGet(() -> PipelineReference.from(fallbackPipeline));
    }

    private void writeTargetMetadata(PipelineCallStation<?, ?> station,
                                     AssemblyLine<?, ?> childPipeline,
                                     StationLogTrace callLog) {
        Map<String, Object> context = callLog.getContext();
        context.put(CONTEXT_PREFIX + "mode", station.getExecutionMode().name());
        context.put(CONTEXT_PREFIX + "declaredReference", station.getTarget().declaredReference().displayName());
        context.put(CONTEXT_PREFIX + "resolvedReference",
                    targetReference(station.getTarget(), childPipeline).displayName());
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
        AssemblyRunTrace execution = result.getExecution();
        String message = execution != null && execution.getErrorMessage() != null ? execution.getErrorMessage()
                : fallbackMessage;
        return new RuntimeException(message);
    }
}

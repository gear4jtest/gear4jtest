package io.github.gear4jtest.studio.runtime;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import io.github.gear4jtest.core.api.ExecutionResult;
import io.github.gear4jtest.core.api.context.ExecutionContext;
import io.github.gear4jtest.core.api.context.StationExecutionContext;
import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.event.StationCancellationReason;
import io.github.gear4jtest.core.event.StationInterruptionReason;
import io.github.gear4jtest.core.event.StationSkipReason;
import io.github.gear4jtest.core.persistence.StationLogRecord;
import io.github.gear4jtest.core.spi.extension.StationLifecycleExtension;
import io.github.gear4jtest.studio.model.CapturedValue;
import io.github.gear4jtest.studio.model.ControlledRun;
import io.github.gear4jtest.studio.model.RunObservation;
import io.github.gear4jtest.studio.spi.DataCapturePolicy;

/**
 * Per-run passive observer for tests or host runs. Metadata is bounded; no raw
 * exception messages, station context, item identifiers or arbitrary toString
 * calls enter the journal. Finish after execute returns to observe the final
 * outcome.
 */
public final class Gear4jRunObserver implements StationLifecycleExtension {
    private final Map<UUID, ControlledRun.Step> steps = new LinkedHashMap<>();
    private final Consumer<RunObservation> progress;
    private final DataCapturePolicy capture;
    private final String artifactHash;
    private final CapturedValue input;
    private RunTrace root;
    private boolean truncated;

    public Gear4jRunObserver(Consumer<RunObservation> progress, DataCapturePolicy capture, String artifactHash) {
        this(progress, capture, artifactHash, null);
    }

    /**
     * Input is sanitized immediately, never retained as a raw object by this
     * observer.
     */
    public Gear4jRunObserver(Consumer<RunObservation> progress,
                             DataCapturePolicy capture,
                             String artifactHash,
                             Object input) {
        this.progress = java.util.Objects.requireNonNull(progress);
        this.capture = java.util.Objects.requireNonNull(capture);
        this.artifactHash = artifactHash;
        this.input = captured(DataCapturePolicy.Target.INPUT, input);
    }

    private synchronized void add(ExecutionContext context, StationLogRecord record) {
        if (root == null)
            root = context.getAssemblyLineExecution();
        var previous = steps.get(record.id());
        if (previous != null && !previous.status().equals("RUNNING") && record.status().name().equals("RUNNING"))
            return;
        if (previous != null || steps.size() < RunObservation.MAX_STEPS)
            steps.put(record.id(), new ControlledRun.Step(record.id(), record.operationId(), record.status().name(),
                    record.startedAt(), record.endedAt(), record.assemblyLineExecutionId(), record.parentOperationId(),
                    record.branchId(), record.status().name().equals("FAILED") ? "EXECUTION_ERROR" : null));
        else
            truncated = true;
        try {
            progress.accept(snapshot(root, "RUNNING", null, null));
        } catch (RuntimeException unavailable) {
            // Progress is best effort; the final write is performed outside engine
            // callbacks.
        }
    }

    public synchronized RunObservation finish(ExecutionResult<?> result) {
        return snapshot(result.getExecution(), result.getOutcome().name(), result.getResult(),
                        result.getError() == null ? null : "EXECUTION_ERROR");
    }

    private RunObservation snapshot(RunTrace run, String status, Object output, String error) {
        Instant start = run == null ? null : run.getStartTime();
        Instant end = status.equals("RUNNING") ? null : run == null ? Instant.now() : run.getEndTime();
        if (!status.equals("RUNNING") && end == null)
            end = Instant.now();
        return new RunObservation(run == null ? null : run.getId(), run == null ? null : run.getParentExecutionId(),
                run == null ? null : run.getRootExecutionId(), status, start, end,
                input,
                status.equals("RUNNING") ? CapturedValue.none() : captured(DataCapturePolicy.Target.OUTPUT, output),
                error, List.copyOf(steps.values()), truncated, artifactHash);
    }

    private CapturedValue captured(DataCapturePolicy.Target target, Object value) {
        if (!(value instanceof String text))
            return CapturedValue.none();
        try {
            var result = capture.capture(target, text);
            return result == null || result.value() != null && result.value().length() > 8192 ? CapturedValue.none()
                    : result;
        } catch (RuntimeException denied) {
            return CapturedValue.none();
        }
    }

    @Override
    public void onStationStarted(ExecutionContext run, StationExecutionContext station, StationLogRecord record) {
        add(run, record);
    }

    @Override
    public void onStationCompleted(ExecutionContext run, StationExecutionContext station, StationLogRecord record) {
        add(run, record);
    }

    @Override
    public void onStationSkipped(ExecutionContext run,
                                 StationExecutionContext station,
                                 StationLogRecord record,
                                 StationSkipReason reason) {
        add(run, record);
    }

    @Override
    public void onStationCancelled(ExecutionContext run,
                                   StationExecutionContext station,
                                   StationLogRecord record,
                                   StationCancellationReason reason,
                                   Exception error) {
        add(run, record);
    }

    @Override
    public void onStationInterrupted(ExecutionContext run,
                                     StationExecutionContext station,
                                     StationLogRecord record,
                                     StationInterruptionReason reason,
                                     String by,
                                     Exception error) {
        add(run, record);
    }

    @Override
    public void onStationFailedBeforeStart(ExecutionContext run,
                                           StationExecutionContext station,
                                           StationLogRecord record,
                                           Exception error) {
        add(run, record);
    }
}

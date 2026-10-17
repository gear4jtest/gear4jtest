package io.github.gear4jtest.studio.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Sanitized runtime snapshot. It never contains exception messages or arbitrary
 * context/log maps.
 */
public record RunObservation(UUID runId,
                             UUID parentRunId,
                             UUID rootRunId,
                             String status,
                             Instant startedAt,
                             Instant endedAt,
                             CapturedValue input,
                             CapturedValue output,
                             String errorCode,
                             List<ControlledRun.Step> steps,
                             boolean stepsTruncated,
                             String artifactHash,
                             ScenarioCheck scenarioCheck) {
    public RunObservation(UUID runId,
                          UUID parentRunId,
                          UUID rootRunId,
                          String status,
                          Instant startedAt,
                          Instant endedAt,
                          CapturedValue input,
                          CapturedValue output,
                          String errorCode,
                          List<ControlledRun.Step> steps,
                          boolean stepsTruncated,
                          String artifactHash) {
        this(runId, parentRunId, rootRunId, status, startedAt, endedAt, input, output, errorCode, steps, stepsTruncated,
                artifactHash, null);
    }

    public static final int MAX_STEPS = 256;
    public static final Set<String> STATUSES = Set.of("PREPARING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED",
                                                      "STOPPED", "SKIPPED", "PREPARATION_FAILED", "UNKNOWN");

    public RunObservation {
        if (!STATUSES.contains(status) || steps.size() > MAX_STEPS
                || artifactHash != null && !artifactHash.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid run observation");
        input = bounded(input);
        output = bounded(output);
        error(errorCode);
        steps = List.copyOf(steps);
        for (var step : steps) {
            Objects.requireNonNull(step.id());
            if (step.nodeId() == null || step.nodeId().length() > 255 || !STATUSES.contains(step.status())
                    || step.branchId() != null && step.branchId().length() > 255)
                throw new IllegalArgumentException("Invalid step observation");
            error(step.errorCode());
        }
    }

    private static void error(String code) {
        if (code != null && !Set.of("EXECUTION_ERROR", "TEST_NOT_ADMITTED", "TEST_OUTCOME_UNKNOWN").contains(code))
            throw new IllegalArgumentException("Only safe error codes may be persisted");
    }

    private static CapturedValue bounded(CapturedValue value) {
        Objects.requireNonNull(value);
        if (value.value() != null && value.value().length() > 8192)
            throw new IllegalArgumentException("Capture exceeds limit");
        return value;
    }

    public boolean terminal() {
        return !status.equals("PREPARING") && !status.equals("RUNNING");
    }

    public static RunObservation completed(ControlledRun run) {
        return new RunObservation(run.runId(), null, run.runId(), run.outcome(), run.startedAt(), run.endedAt(),
                run.input(), run.output(), run.errorCode(), run.steps().stream().limit(MAX_STEPS).toList(),
                run.stepsTruncated() || run.steps().size() > MAX_STEPS, run.artifactHash(), run.scenarioCheck());
    }

    @Override
    public String toString() {
        return "RunObservation[runId=" + runId + ", status=" + status + ", steps=" + steps.size() + "]";
    }
}

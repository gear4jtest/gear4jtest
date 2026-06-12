package io.github.gear4jtest.micrometer;

import java.util.Objects;

import io.github.gear4jtest.core.execution.trace.AssemblyRunTrace;
import io.github.gear4jtest.core.persistence.StationLogRecord;

/**
 * Controls Micrometer tag cardinality for Gear4J metrics.
 *
 * <p>
 * The default policy preserves the historic tags. Applications with dynamic
 * pipeline or operation identifiers can provide a custom bean to reduce
 * high-cardinality tags before exporting metrics to Prometheus, Datadog or a
 * similar backend.
 * </p>
 */
public interface Gear4jMeterTagPolicy {
    Gear4jMeterTagPolicy DEFAULT = new DefaultGear4jMeterTagPolicy();

    String[] runStartedTags(AssemblyRunTrace run);

    String[] runCompletedTags(AssemblyRunTrace run);

    String[] stationStartedTags(StationLogRecord station);

    String[] stationCompletedTags(StationLogRecord station);

    static Gear4jMeterTagPolicy defaults() {
        return DEFAULT;
    }

    static String safe(Object value) {
        return value == null ? "unknown" : String.valueOf(value);
    }

    final class DefaultGear4jMeterTagPolicy implements Gear4jMeterTagPolicy {
        @Override
        public String[] runStartedTags(AssemblyRunTrace run) {
            Objects.requireNonNull(run, "run must not be null");
            return new String[] { "pipeline.id", safe(run.getPipelineId()) };
        }

        @Override
        public String[] runCompletedTags(AssemblyRunTrace run) {
            Objects.requireNonNull(run, "run must not be null");
            return new String[] { "pipeline.id", safe(run.getPipelineId()), "status", safe(run.getStatus()) };
        }

        @Override
        public String[] stationStartedTags(StationLogRecord station) {
            Objects.requireNonNull(station, "station must not be null");
            return new String[] { "operation.id", safe(station.operationId()), "branch.id", safe(station.branchId()) };
        }

        @Override
        public String[] stationCompletedTags(StationLogRecord station) {
            Objects.requireNonNull(station, "station must not be null");
            return new String[] { "operation.id", safe(station.operationId()), "branch.id", safe(station.branchId()),
                    "status", safe(station.status()) };
        }
    }
}

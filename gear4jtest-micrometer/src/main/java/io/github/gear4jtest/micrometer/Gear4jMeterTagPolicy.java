package io.github.gear4jtest.micrometer;

import java.util.Objects;
import java.util.Set;

import io.github.gear4jtest.core.api.trace.RunTrace;
import io.github.gear4jtest.core.persistence.StationLogRecord;

/**
 * Controls Micrometer tag cardinality for Gear4J metrics.
 *
 * <p>
 * The default policy emits only bounded status tags. Pipeline, operation and
 * branch identifiers require an explicit allowlist or a custom policy.
 * </p>
 */
public interface Gear4jMeterTagPolicy {
    Gear4jMeterTagPolicy DEFAULT = new DefaultGear4jMeterTagPolicy();

    String[] runStartedTags(RunTrace run);

    String[] runCompletedTags(RunTrace run);

    String[] stationStartedTags(StationLogRecord station);

    String[] stationCompletedTags(StationLogRecord station);

    static Gear4jMeterTagPolicy defaults() {
        return DEFAULT;
    }

    static Gear4jMeterTagPolicy allowlistedIdentifiers(Set<String> pipelineIds,
                                                       Set<String> operationIds,
                                                       Set<String> branchIds) {
        return new AllowlistedGear4jMeterTagPolicy(pipelineIds, operationIds, branchIds);
    }

    /**
     * Historic unbounded identifier tags retained only as a migration aid.
     *
     * @deprecated Prefer {@link #defaults()} or
     *             {@link #allowlistedIdentifiers(Set, Set, Set)}.
     */
    @Deprecated(forRemoval = true)
    static Gear4jMeterTagPolicy legacyIdentifiers() {
        return LegacyIdentifierGear4jMeterTagPolicy.INSTANCE;
    }

    static String safe(Object value) {
        return value == null ? "unknown" : String.valueOf(value);
    }

    final class DefaultGear4jMeterTagPolicy implements Gear4jMeterTagPolicy {
        @Override
        public String[] runStartedTags(RunTrace run) {
            Objects.requireNonNull(run, "run must not be null");
            return new String[0];
        }

        @Override
        public String[] runCompletedTags(RunTrace run) {
            Objects.requireNonNull(run, "run must not be null");
            return new String[] { "status", safe(run.getStatus()) };
        }

        @Override
        public String[] stationStartedTags(StationLogRecord station) {
            Objects.requireNonNull(station, "station must not be null");
            return new String[0];
        }

        @Override
        public String[] stationCompletedTags(StationLogRecord station) {
            Objects.requireNonNull(station, "station must not be null");
            return new String[] { "status", safe(station.status()) };
        }
    }

    final class AllowlistedGear4jMeterTagPolicy implements Gear4jMeterTagPolicy {
        private static final String OTHER = "other";
        private final Set<String> pipelineIds;
        private final Set<String> operationIds;
        private final Set<String> branchIds;

        AllowlistedGear4jMeterTagPolicy(Set<String> pipelineIds,
                                        Set<String> operationIds,
                                        Set<String> branchIds) {
            this.pipelineIds = Set.copyOf(Objects.requireNonNull(pipelineIds, "pipelineIds must not be null"));
            this.operationIds = Set.copyOf(Objects.requireNonNull(operationIds, "operationIds must not be null"));
            this.branchIds = Set.copyOf(Objects.requireNonNull(branchIds, "branchIds must not be null"));
        }

        @Override
        public String[] runStartedTags(RunTrace run) {
            Objects.requireNonNull(run, "run must not be null");
            return new String[] { "pipeline.id", allowlisted(run.getAssemblyLineId(), pipelineIds) };
        }

        @Override
        public String[] runCompletedTags(RunTrace run) {
            Objects.requireNonNull(run, "run must not be null");
            return new String[] { "pipeline.id", allowlisted(run.getAssemblyLineId(), pipelineIds), "status",
                    safe(run.getStatus()) };
        }

        @Override
        public String[] stationStartedTags(StationLogRecord station) {
            Objects.requireNonNull(station, "station must not be null");
            return stationIdentityTags(station);
        }

        @Override
        public String[] stationCompletedTags(StationLogRecord station) {
            Objects.requireNonNull(station, "station must not be null");
            String[] identity = stationIdentityTags(station);
            return new String[] { identity[0], identity[1], identity[2], identity[3], "status",
                    safe(station.status()) };
        }

        private String[] stationIdentityTags(StationLogRecord station) {
            return new String[] { "operation.id", allowlisted(station.operationId(), operationIds), "branch.id",
                    allowlisted(station.branchId(), branchIds) };
        }

        private static String allowlisted(String value, Set<String> allowlist) {
            return value != null && allowlist.contains(value) ? value : OTHER;
        }
    }

    enum LegacyIdentifierGear4jMeterTagPolicy implements Gear4jMeterTagPolicy {
        INSTANCE;

        @Override
        public String[] runStartedTags(RunTrace run) {
            Objects.requireNonNull(run, "run must not be null");
            return new String[] { "pipeline.id", safe(run.getAssemblyLineId()) };
        }

        @Override
        public String[] runCompletedTags(RunTrace run) {
            Objects.requireNonNull(run, "run must not be null");
            return new String[] { "pipeline.id", safe(run.getAssemblyLineId()), "status", safe(run.getStatus()) };
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

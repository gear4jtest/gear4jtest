package io.github.gear4jtest.core.api.config;

import java.util.OptionalInt;

public class PersistenceConfiguration {

    // Business choice: storing the final result improves auditability at the cost
    // of storage.
    private final boolean storeResultObject;
    private final Integer stationLogFlushThreshold;

    private PersistenceConfiguration(boolean storeResultObject, Integer stationLogFlushThreshold) {
        this.storeResultObject = storeResultObject;
        this.stationLogFlushThreshold = stationLogFlushThreshold;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isStoreResultObject() {
        return storeResultObject;
    }

    /**
     * Returns the number of station-log snapshots after which the persistence
     * manager should schedule a flush for this run.
     *
     * <p>
     * An empty value delegates the decision to the persistence manager. The value
     * controls persistence batching only; station lifecycle orchestration still
     * emits every snapshot exactly once.
     * </p>
     */
    public OptionalInt getStationLogFlushThreshold() {
        return stationLogFlushThreshold == null ? OptionalInt.empty()
                : OptionalInt.of(stationLogFlushThreshold);
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static class Builder {
        private boolean storeResultObject = true;
        private Integer stationLogFlushThreshold;

        public Builder() {
        }

        private Builder(PersistenceConfiguration source) {
            this.storeResultObject = source.storeResultObject;
            this.stationLogFlushThreshold = source.stationLogFlushThreshold;
        }

        public Builder storeResultObject(boolean storeResultObject) {
            this.storeResultObject = storeResultObject;
            return this;
        }

        /**
         * Overrides the persistence manager's station-log flush threshold for one
         * assembly line or run.
         */
        public Builder stationLogFlushThreshold(int stationLogFlushThreshold) {
            this.stationLogFlushThreshold = stationLogFlushThreshold;
            return this;
        }

        public PersistenceConfiguration build() {
            if (stationLogFlushThreshold != null && stationLogFlushThreshold <= 0) {
                throw new IllegalArgumentException("stationLogFlushThreshold must be > 0");
            }
            return new PersistenceConfiguration(storeResultObject, stationLogFlushThreshold);
        }
    }
}

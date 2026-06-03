package io.github.gear4jtest.core.api.config;

public class PersistenceConfiguration {

    // Business choice: storing the final result improves auditability at the cost
    // of storage.
    private final boolean storeResultObject;

    private PersistenceConfiguration(boolean storeResultObject) {
        this.storeResultObject = storeResultObject;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isStoreResultObject() {
        return storeResultObject;
    }

    public static class Builder {
        private boolean storeResultObject = true;

        public Builder storeResultObject(boolean storeResultObject) {
            this.storeResultObject = storeResultObject;
            return this;
        }

        public PersistenceConfiguration build() {
            return new PersistenceConfiguration(storeResultObject);
        }
    }
}

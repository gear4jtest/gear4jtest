package io.github.gear4jtest.core.model.refactor;

import javax.sql.DataSource;

public class PersistenceConfiguration {
    private PersistenceType persistenceType;
    private DataSource dataSource;
    private DataSourceType dataSourceType;
    private boolean storeResultObject;

    public PersistenceConfiguration() {
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public boolean isStoreResultObject() {
        return storeResultObject;
    }

    public DataSourceType getDataSourceType() {
        return dataSourceType;
    }

    public PersistenceType getPersistenceType() {
        return persistenceType;
    }

    public static class Builder {

        private final PersistenceConfiguration managedInstance;

        public Builder() {
            managedInstance = new PersistenceConfiguration();
        }

        public PersistenceConfiguration.Builder persistenceType(PersistenceType persistenceType) {
            managedInstance.persistenceType = persistenceType;
            return this;
        }

        public PersistenceConfiguration.Builder dataSource(DataSource dataSource) {
            managedInstance.dataSource = dataSource;
            return this;
        }

        public PersistenceConfiguration.Builder storeResultObject(boolean storeResultObject) {
            managedInstance.storeResultObject = storeResultObject;
            return this;
        }

        public PersistenceConfiguration.Builder dataSourceType(DataSourceType dataSourceType) {
            managedInstance.dataSourceType = dataSourceType;
            return this;
        }

        public PersistenceConfiguration build() {
            return managedInstance;
        }

    }

    public enum DataSourceType {
        H2, MYSQL, POSTGRESQL
    }

    public enum PersistenceType {
        IN_MEMORY, DATABASE
    }
}

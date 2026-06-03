# JDBC database dialect is explicit configuration

## Status

Implemented.

## Decision

Gear4J JDBC components require applications to provide a `Gear4jDatabaseDialect` value explicitly.

Supported values for the current MVP are:

- `POSTGRESQL`;
- `MYSQL`;
- `MARIADB`;
- `ORACLE`;
- `H2` for tests and local development.

There is no automatic dialect detection and no `AUTO` mode.

## Rationale

The selected dialect does more than tune an optional query. It controls:

- schema resources;
- SQL upsert syntax;
- JSON/CLOB binding;
- UUID binding;
- duplicate-key handling;
- generated-key behavior.

Inferring this configuration from JDBC metadata is not reliable enough for a library: drivers, wrappers, proxies and
compatible providers may expose ambiguous metadata. A wrong inference may execute incompatible SQL after a run has
started.

Requiring the dialect at construction time makes the configuration deterministic and turns a missing choice into a
pre-execution configuration error.

## API rule

JDBC-facing constructors must require the shared public enum:

```java
new DatabaseExecutionManager(dataSource, Gear4jDatabaseDialect.POSTGRESQL);
new DatabaseAssemblyRunRepository(dataSource, Gear4jDatabaseDialect.POSTGRESQL);
new OperationChainConfigRepositoryJdbc(dataSource, Gear4jDatabaseDialect.POSTGRESQL);
new OperationChainObjectRepositoryJdbc(dataSource, Gear4jDatabaseDialect.POSTGRESQL);
new OperationChainTagRepositoryJdbc(dataSource, Gear4jDatabaseDialect.POSTGRESQL);
new DatabaseArtifactStore(dataSource, "artifact_store", Gear4jDatabaseDialect.POSTGRESQL);
```

The `DATABASE` artifact-store plugin must require a `dialect` property instead of consulting the `DataSource` metadata.

## Module boundary

`Gear4jDatabaseDialect` belongs to `gear4jtest-core` and is the single public database choice used by all modules.
Each JDBC-owning module keeps its SQL implementation local; the shared enum is not intended to become a large generic
SQL abstraction.

## Consequence

Adding a newly supported database requires an explicit enum addition, appropriate SQL/schema implementation and tests.
Unknown or missing choices fail before pipeline execution rather than falling back to an inferred dialect.

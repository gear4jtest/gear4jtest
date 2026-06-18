# JDBC schema migrations

Gear4J ships versioned SQL migrations for its internal JDBC schemas.

## Core runtime schema

`DatabaseAssemblyRunRepository.initialize()` applies the core migrations for the
explicitly configured `Gear4jDatabaseDialect` when Gear4J is allowed to manage
its own schema.

Resources are stored under:

```text
io/github/gear4j/db/<dialect>/migrations/
```

## External API schema

External repositories can be initialized with:

```java
ExternalJdbcSchemaMigrator.forDialect(Gear4jDatabaseDialect.POSTGRESQL)
        .migrate(dataSource);
```

Resources are stored under:

```text
io/github/gear4j/external/db/<dialect>/migrations/
```

## History table

Applied migrations are tracked in:

```text
gear4j_schema_history
```

The primary key is `(module_id, version)`, so core and external-api migrations
can share the same history table without collisions.

## Using Gear4J-managed migrations

This is the simplest mode. The application gives Gear4J a `DataSource`, an
explicit dialect, and allows Gear4J to create or evolve its own schema.

Spring Boot example:

```yaml
gear4j:
  persistence:
    enabled: true
    dialect: POSTGRESQL
    auto-create-tables: true
```

Plain Java example:

```java
DatabaseAssemblyRunRepository repository = DatabaseAssemblyRunRepository.builder()
        .dataSource(dataSource)
        .databaseDialect(Gear4jDatabaseDialect.POSTGRESQL)
        .build();
```

This mode is convenient for demos, standalone services, or applications where
Gear4J owns its persistence schema.

## Using Flyway or another application-owned migrator

Many production applications already centralize every schema change through
Flyway, Liquibase, or an internal migration process. In that case, do not let
Gear4J mutate the schema at runtime.

Spring Boot example:

```yaml
gear4j:
  persistence:
    enabled: true
    dialect: POSTGRESQL
    auto-create-tables: false
```

Then copy the SQL content shipped by Gear4J into the application's own migration
folder and version it according to the application's migration sequence:

```text
src/main/resources/db/migration/postgresql/
  V2026060101__create_gear4j_core_schema.sql
  V2026060102__create_gear4j_external_schema.sql
```

For example, the application migration can contain the SQL from:

```text
io/github/gear4j/db/postgresql/migrations/V1__create_execution_schema.sql
io/github/gear4j/external/db/postgresql/migrations/V1__create_external_schema.sql
```

The application then keeps its normal Flyway configuration, for example:

```yaml
spring:
  flyway:
    locations: classpath:db/migration/postgresql
```

This approach avoids version collisions between Gear4J's internal migration
numbers and the host application's migration numbers. It also keeps DB review,
approval, rollback planning and release ownership in the application.

## Dedicated Flyway instance for Gear4J

A future Spring Boot starter enhancement may expose a dedicated Gear4J Flyway
configuration, for example with a separate history table such as
`gear4j_flyway_schema_history` and locations pointing directly to Gear4J SQL
resources.

That mode is not implemented today. Until it exists, production applications that
want Flyway ownership should vendor/copy Gear4J SQL into their own migration
sequence and run with `auto-create-tables=false`.

## Existing schemas

If a known initial table already exists and no Gear4J history is present, the
first Gear4J migration can be recorded as already applied instead of re-applied.
This keeps the migrator compatible with users upgrading from the previous
`tableExists => skip initialization` behavior.

This compatibility behavior is not a schema validator. It does not prove that an
existing table exactly matches the shipped SQL. Applications with strict DB
requirements should manage the SQL through their own migration process and review
it explicitly.

## Station-log write strategy

Core station-log persistence preserves finalized rows: once a station log has an `end_time`, later writes for the same id
are deliberately ignored by the normal persistence path instead of overwriting the final record.

For PostgreSQL, MySQL and MariaDB, `DatabaseAssemblyRunRepository` uses a native batched upsert that inserts missing logs
and updates existing logs only while the stored row is still open. H2 and Oracle keep the portable update-then-insert path.
Oracle stays on the portable path until its native `MERGE` variant is covered by integration tests for the CLOB/JSON
bindings used by Gear4J.

## Runtime locking and transaction boundaries

Gear4J-managed migrations now create and use a lightweight portable lock table:

```text
gear4j_schema_lock
```

Before applying migrations for a module, `JdbcSchemaMigrator` acquires a row lock
on the module row with `SELECT ... FOR UPDATE`. This is intended to prevent two
application instances from applying the same Gear4J schema migrations at the same
time. The lock is scoped to the JDBC transaction and is released when the
migration transaction commits or rolls back.

When the migrator obtains a connection from a `DataSource`, it owns the migration
transaction: it disables auto-commit, creates/updates the schema infrastructure,
applies pending migrations and commits. If a caller passes a connection that is
already inside a transaction, Gear4J does not commit or roll back the caller's
transaction; the caller remains responsible for the transaction boundary.

DDL transaction semantics still depend on the database. PostgreSQL and H2 can
usually keep DDL transactional. MySQL/MariaDB and Oracle may auto-commit DDL in
some cases, so the lock/history mechanism should be considered a robustness
guardrail rather than a full Flyway/Liquibase replacement.

## Baseline validation

The compatibility baseline path is no longer a blind `tableExists` shortcut. If
no Gear4J history exists and the initial table is already present, the migrator
now validates that the tables declared by the first migration exist before
recording that migration as applied. For the core schema, it also checks the
minimum columns required by the runtime for `assembly_run` and `station_log`.

This validation is intentionally minimal. It catches incomplete legacy schemas,
but it does not replace a full schema diff tool. Production applications that
need reviewed, reversible DB changes should still vendor/copy the Gear4J SQL into
their own Flyway/Liquibase process and run Gear4J with `auto-create-tables=false`.

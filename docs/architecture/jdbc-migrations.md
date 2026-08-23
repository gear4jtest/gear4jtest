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

Migration attempts and applied migrations are tracked in:

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

External migration V1 creates the final `idx_op_chain_latest_run` definition.
PostgreSQL uses a partial RUN-only index ordered by `al_id`, `published_at DESC`
and `id DESC`. H2, MySQL, MariaDB and Oracle use `al_id`, `publication_mode`,
`published_at DESC` and `id DESC`.

The same unreleased V1 schema also creates `idx_op_chain_all` on `al_id`,
`published_at DESC`, `id DESC` for keyset consistency scans. Publication-stage
maintenance uses `idx_op_chain_stage_age` on `staged_at`, `stage_id`. These are
V1 changes because no public Gear4J release exists; no compatibility V2
migration is carried for a schema that has never been released.

Artifact store identifiers are also open in that V1 schema. Every dialect uses
`VARCHAR(64)` (Oracle uses `VARCHAR2(64)`) plus the canonical format check
`[A-Z][A-Z0-9_-]{0,63}`. MySQL and MariaDB deliberately do not use a database
enum: `ArtifactStorePlugin` implementations supplied by applications must be
persistable without a Gear4J schema release. Development schemas created from
an older V1 must be recreated; this pre-1.0 correction does not add a V2.

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

## Execution-history indexes

The V1 execution schema includes ordered-pagination indexes matching the full
repository order, including the `id` tie-breaker. Assembly-run history uses
`(assembly_line_id, start_time, id)`, `(status, start_time, id)` and
`(start_time, id)`. Station-log hierarchy pages use
`(assembly_line_execution_id, parent_log_id, start_time, id)`; all logs for one
run use `(assembly_line_execution_id, start_time, id)`.

These definitions are qualified against PostgreSQL, MySQL, MariaDB and Oracle
at representative library-level cardinalities. Because Gear4J remains pre-1.0
with no production adopters, the index corrections stay in V1 rather than
adding a compatibility migration. Existing development schemas created from an
older V1 must be recreated before running this version.

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

Internally, `JdbcSchemaMigrator` remains the transaction and migration orchestrator. `MigrationHistoryStore` owns durable
state transitions, `MigrationLockStore` owns the portable lock table and row acquisition, and
`MigrationResourceLoader` owns list parsing, resource loading and checksums. These components are package-private and do
not expand the public migration API.

When the migrator obtains a connection from a `DataSource`, it owns the migration
transaction: it disables auto-commit, creates/updates the schema infrastructure,
applies pending migrations and commits. If a caller passes a connection that is
already inside a transaction, Gear4J does not commit or roll back the caller's
transaction; the caller remains responsible for the transaction boundary.

DDL transaction semantics still depend on the database. PostgreSQL and H2 can
usually keep DDL transactional. MySQL/MariaDB and Oracle may auto-commit DDL in
some cases, so the lock/history mechanism should be considered a robustness
guardrail rather than a full Flyway/Liquibase replacement.

## Partial-migration recovery runbook

Gear4J records every managed migration with one of three durable states in
`gear4j_schema_history.migration_state`:

- `STARTED`: execution began but no successful completion was recorded;
- `APPLIED`: every statement completed and the checksum was accepted;
- `FAILED`: execution raised an error and Gear4J was able to persist the failure
  marker.

Existing history rows created by an earlier Gear4J version are upgraded with an
`APPLIED` default. A new startup never retries `STARTED` or `FAILED`
automatically. This is deliberate: MySQL, MariaDB and Oracle may have committed
only part of the DDL even though the surrounding JDBC transaction was rolled
back.

Use the following procedure after a migration failure or an application
termination during migration:

1. Stop every application instance that can run Gear4J-managed migrations and
   keep `auto-create-tables` disabled during diagnosis.
2. Take the normal database backup or snapshot required by the application's
   operational policy.
3. Inspect the durable state through the API:

   ```java
   List<SchemaMigrationStatus> statuses =
           JdbcSchemaMigrator.core(dialect).migrationStatuses(dataSource);
   ```

   The equivalent diagnostic SQL is:

   ```sql
   SELECT module_id, version, description, checksum, migration_state, installed_at
   FROM gear4j_schema_history
   ORDER BY module_id, installed_at, version;
   ```

4. Compare the actual tables, columns, constraints and indexes with the bundled
   SQL resource for the exact dialect and version. The relevant resources are
   under `io/github/gear4j/db/<dialect>/migrations/` and
   `io/github/gear4j/external/db/<dialect>/migrations/`.
5. Choose one recovery path:
   - if the DDL was fully rolled back, no application object should remain;
   - if the DDL was partially committed, remove or complete only the objects
     identified by the comparison, using a reviewed database change;
   - for a fully present V1 schema, `baselineOnMigrate=true` may be used only
     after the failed marker is cleared; Gear4J then validates its required
     tables, columns and indexes before recording the baseline.
6. After the schema is known to be safe for another attempt, prepare the retry:

   ```java
   JdbcSchemaMigrator.core(dialect).prepareRetry(dataSource, "1");
   ```

   For the external schema:

   ```java
   ExternalJdbcSchemaMigrator.forDialect(dialect).prepareRetry(dataSource, "1");
   ```

   `prepareRetry` acquires the module migration lock, verifies that the stored
   checksum still matches the bundled migration and deletes only a `STARTED` or
   `FAILED` marker. It does not create, drop, alter or validate application
   schema objects.
7. Re-enable migration on one application instance, verify that the state
   becomes `APPLIED`, then restore the normal instance count.

Never change a migration checksum or convert a failed row to `APPLIED` directly.
If the actual schema cannot be reconciled safely, restore the database snapshot
or move the scripts into the application's Flyway/Liquibase process and keep
Gear4J auto-creation disabled.

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

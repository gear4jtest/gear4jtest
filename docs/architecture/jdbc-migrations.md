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
DatabaseAssemblyRunRepository repository = new DatabaseAssemblyRunRepository(
        dataSource,
        Gear4jDatabaseDialect.POSTGRESQL,
        true);
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

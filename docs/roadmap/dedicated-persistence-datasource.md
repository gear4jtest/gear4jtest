# Dedicated Gear4J persistence datasource

## Document control

| Field | Value |
| --- | --- |
| Status | Future direction; not implemented |
| Owner | Gear4J maintainers |
| Last reviewed | 2026-08-12 |
| Target version | Post-1.0; unscheduled |

## Context

Gear4J persistence can write run records and station logs independently from the host application's main business
transactions. In production, especially with verbose station logging or asynchronous flushes, these writes may compete
with application traffic for database connections.

## Idea

Allow applications to provide a dedicated `DataSource` or connection pool for Gear4J persistence.

Potential benefits:

- isolate station-log writes from application transactional workloads;
- configure a smaller pool with shorter timeouts for observability writes;
- point Gear4J persistence at a dedicated schema or database;
- reduce the operational blast radius of slow persistence flushes;
- prepare future async/batch persistence strategies.

## Important rule

A dedicated pool does not remove the need for JDBC hygiene. Repository methods must still restore connection state such
as `autoCommit`, `readOnly`, isolation level or schema/catalog after changing it.

The pool can reduce blast radius, but each repository method must remain correct with any caller-provided `DataSource`.

## Possible Spring integration

A future Spring module may provide clearer wiring around a Gear4J-specific datasource, for example by accepting a bean
reference or configuration property that points persistence components to a dedicated `DataSource`.

## Non-goal

Do not add a concrete pooling dependency such as HikariCP to `gear4jtest-core`.

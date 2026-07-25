# ADR 0032: JDBC repository writes use autonomous transactions

- Status: Accepted
- Date: 2026-07-24

## Context

Gear4J repositories previously acquired a connection, disabled auto-commit and
called `commit` or `rollback` directly. That is valid for a plain pooled
`DataSource`, but unsafe when a framework proxy returns a connection already
bound to an application transaction. A repository could then commit or roll back
work outside Gear4J.

JDBC run persistence also buffers station logs and flushes them asynchronously.
Those writes cannot consistently participate in the transaction that initiated
the run because they may occur later and on another thread.

## Decision

Repository write transaction ownership is represented by
`JdbcTransactionOperations`.

- The generic default is library-owned and autonomous. It acquires one fresh
  connection per write, requires initial `autoCommit=true`, commits on success,
  rolls back on failure and closes the connection.
- A connection already inside a transaction is rejected before callback SQL
  executes. Gear4J never guesses that it owns an `autoCommit=false` connection.
- Repository SQL callbacks do not manage auto-commit, commit, rollback or
  connection closure.
- The Spring Boot adapter delegates the boundary to
  `DataSourceTransactionManager` with `PROPAGATION_REQUIRES_NEW`. An ambient
  transaction is suspended and resumed around the independent Gear4J write.
- Auto-configuration verifies that the transaction manager owns the same target
  datasource as the repository, unwrapping `TransactionAwareDataSourceProxy`
  before comparison.
- A custom `JdbcTransactionOperations` can replace either default.

This contract applies to `DatabaseAssemblyRunRepository` writes and
`OperationChainObjectRepositoryJdbc` publication writes. Read-only repository
operations retain normal `DataSource` semantics. Schema migration keeps its
separate documented contract because DDL ownership and database-specific
implicit commits require different recovery rules.

## Consequences

- Gear4J cannot accidentally commit or roll back a caller-owned Spring
  transaction.
- A pooled datasource configured to return `autoCommit=false` must provide an
  explicit transaction adapter instead of relying on the generic default.
- Spring persistence writes are independent from the host transaction, including
  synchronous run start/end writes.
- `REQUIRES_NEW` can hold an outer connection while acquiring a second one; the
  application pool must be sized with at least one connection of headroom beyond
  concurrent outer-transaction threads.
- Atomic participation in a wider business transaction is not offered by the
  asynchronous persistence runtime. A future synchronous repository integration
  would need a separate explicit mode and lifecycle contract.

## Verification

Tests cover:

- generic commit, rollback, cleanup failures and rejection of an
  `autoCommit=false` connection;
- `TransactionAwareDataSourceProxy` inside an ambient transaction;
- Spring execution with no ambient transaction;
- `REQUIRES_NEW` commit surviving rollback of the outer transaction;
- rollback and checked `SQLException` preservation in the Spring adapter.

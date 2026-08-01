# 0034 — Latest RUN indexes match filter and order

## Status

Accepted.

## Context

`OperationChainObjectRepositoryJdbc.findLatestRun` filters by assembly-line id
and RUN publication mode, then orders by publication time and the generated id.
The original PostgreSQL partial index omitted the id tie-breaker. The other
dialects omitted publication mode and id, so histories containing many newer
TEST publications could require additional filtering or sorting.

The database also saw an ordered query combined with JDBC `setMaxRows(1)`.
Driver behavior for that hint is not a sufficiently explicit cross-dialect
top-one contract.

## Decision

- PostgreSQL keeps a selective partial index for RUN publications:
  `(al_id, published_at DESC, id DESC) WHERE publication_mode = 'RUN'`.
- H2, MySQL, MariaDB and Oracle use:
  `(al_id, publication_mode, published_at DESC, id DESC)`.
- Migration V2 drops and recreates the existing index under the same stable
  name. V1 is not rewritten, so already-applied migration checksums remain
  valid.
- `findLatestRun` uses the repository's dialect-specific SQL pagination with a
  limit of one. The row bound is therefore visible to the optimizer.
- The multi-dialect integration suite loads a TEST-heavy history, captures the
  execution plan, requires the plan to use the named index and records a
  non-gating lookup duration.

## Consequences

- Filter, ordering and deterministic tie-breaking are covered by one index.
- PostgreSQL does not index TEST rows for this query.
- Other dialects retain one portable composite index because partial indexes
  are not uniformly available.
- The number of secondary indexes does not increase: V2 replaces the old
  latest-RUN index, limiting additional write amplification to the extra key
  columns.
- Applications that manage Gear4J SQL through Flyway or Liquibase must apply
  the V2 replacement in their own migration sequence.

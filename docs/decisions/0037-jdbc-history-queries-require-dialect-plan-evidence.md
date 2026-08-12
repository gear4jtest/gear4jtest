# 0037 - JDBC history queries require dialect plan evidence

## Status

Accepted - 2026-08-12

## Context

Functional Testcontainers coverage proved SQL compatibility but did not prove
that execution-history queries selected their intended indexes at meaningful
cardinality. Several V1 indexes also omitted the `id` tie-breaker present in the
repository ordering, and global history had no matching temporal index.

Response-time assertions across four containerized engines can become flaky if
treated as production SLOs. Exact optimizer choices are also not a portable
contract. Connected runs showed PostgreSQL preferring the narrower
`idx_ar_status` before a small top-N sort and MySQL preferring the foreign-key
`fk_parent_op` for a globally unique parent id. Both plans were indexed and
fast, despite not naming the composite reference index.

The stable invariants are therefore the ordered-index definitions, repository
results and a generous catastrophic-regression ceiling. Natural plans remain
essential evidence, but their selected access path is observed rather than
prescribed.

## Decision

- Extend the existing PostgreSQL, MySQL, MariaDB and Oracle repository matrix;
  do not start a second container solely for plan tests.
- Seed 20,000 assembly runs and 10,000 station logs per dialect job, with
  selective assembly-line, status, hierarchy and run-local distributions.
- Qualify six critical paged reads after refreshing optimizer statistics.
- Verify the five ordered-index column lists independently through connected
  JDBC metadata.
- Capture the natural plan and record whether it selects the reference index
  and whether it contains a full scan; do not force or require one optimizer
  choice with hints or exact-plan assertions.
- Record three warmups, nine measured calls, p50, p95, maximum duration and the
  raw engine plan in a per-dialect Markdown artifact.
- Use engine-native actual-plan facilities for PostgreSQL, MySQL and MariaDB.
  Oracle combines timed execution with `EXPLAIN PLAN` and `DBMS_XPLAN` because
  obtaining cursor statistics would require broader catalog privileges.
- Keep a loose two-second ceiling as a portable catastrophic guardrail. It is
  not a production SLO and cannot replace application-specific sizing.
- Complete V1 ordered-pagination indexes with the `id` tie-breaker and add the
  missing global `(start_time, id)` index. H2 receives the same schema shape but
  is not counted as production plan evidence.

## Consequences

The four mandatory JDBC jobs fail when an ordered index has the wrong definition,
plan evidence is missing, repository results differ or a measured call crosses
the catastrophic ceiling. They do not fail merely because an optimizer selects
a valid alternative index or scan. Release artifacts expose that choice, any
full scan and the timing distribution for review.

The V1 migration checksum changes. This is accepted because Gear4J has no
production adopters and the project deliberately keeps pre-1.0 schema changes
in V1. Older development databases must be recreated. Future production
retention and data skew still require workload-specific `EXPLAIN` evidence.

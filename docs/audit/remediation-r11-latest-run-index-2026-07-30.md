# R11 — `findLatestRun` index and plan evidence

R11 closes audit finding F-14.

## Behavioral change

`findLatestRun` now sends an explicit dialect-specific top-one query instead of
relying on `PreparedStatement.setMaxRows(1)`. External schema migration V2
replaces `idx_op_chain_latest_run` with a definition covering the RUN filter,
the descending publication order and the `id` tie-breaker.

PostgreSQL retains a partial RUN-only index. H2, MySQL, MariaDB and Oracle use a
portable composite index including `publication_mode`.

## Verification

The four-dialect Testcontainers suite loads 20,000 rows per database across 20
assembly lines. Each line has 990 newer TEST rows and 10 RUN rows. The suite:

- verifies the V2 index definition;
- proves that the latest RUN remains correct despite newer TEST rows;
- captures the real optimizer plan and requires the new index to be selected;
- records, without a flaky threshold, 50 prepared-query timings after warmup.

H2 migration coverage additionally verifies that V1 and V2 are both recorded
and that the migrated index has the expected four columns.

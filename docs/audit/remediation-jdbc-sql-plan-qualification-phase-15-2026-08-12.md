# JDBC SQL-plan qualification - phase 15

**Date:** 12 August 2026
**Scope:** representative execution-history plans and response times

## Objective

Close audit acceptance scenario 11: retain SQL plans and response-time evidence
at representative cardinalities for PostgreSQL, MySQL, MariaDB and Oracle.
H2 remains useful for fast functional feedback but does not stand in for a
production optimizer.

## Implemented qualification

The existing core repository multi-dialect test now reuses its already-started
container and adds:

- 20,000 assembly runs across 200 assembly-line values, including a selective
  failure status;
- 5,000 station logs for a target run and 5,000 for a noise run, with 100 roots
  and deterministic child distribution per run;
- refreshed engine statistics before plan capture;
- repository-result validation plus three warmups and nine timed executions on
  one connection;
- native plan capture for the six critical paged history queries;
- connected verification of the five ordered-index column lists, independently
  from optimizer choice;
- per-dialect Markdown evidence containing p50, p95, maximum time, whether the
  reference index was selected, whether a full scan was observed and raw plans.

PostgreSQL uses `EXPLAIN ANALYZE`, MySQL uses `EXPLAIN ANALYZE FORMAT=TREE`,
MariaDB uses `ANALYZE FORMAT=JSON`, and Oracle uses timed execution plus
`EXPLAIN PLAN`/`DBMS_XPLAN`. Reports are uploaded with the JDBC CI and release
artifacts.

## Connected feedback correction

The first connected execution exposed an over-constrained assertion rather than
three schema defects:

- PostgreSQL used `idx_ar_status` to fetch 200 failure rows, then completed a
  top-N sort in about one millisecond;
- MySQL used its required `fk_parent_op` index for the globally unique parent id
  and filtered the run id from 49 rows;
- Oracle produced a natural plan that did not name the reference composite
  index at this cardinality and session collation.

Forcing the composite name would turn optimizer cost decisions into a brittle
cross-dialect contract. The correction verifies index structure directly,
retains the natural decision and timing in the report, and makes result
correctness plus the catastrophic ceiling the runtime guardrails. No optimizer
hints or artificial cardinality changes are introduced.

## Schema correction justified by the qualification

The V1 ordered-history indexes now include the repository's `id` tie-breaker.
Global history gains `idx_ar_start (start_time, id)`, while hierarchical station
logs use `(assembly_line_execution_id, parent_log_id, start_time, id)`.
Baseline validation requires the new global index.

Gear4J has no production adopters, so this intentionally updates V1 rather than
introducing a compatibility migration. Existing development databases must be
recreated.

## Guardrail policy

The two-second ceiling per measured call exists only to detect catastrophic
regression on shared runners. It is not a latency SLO. A production deployment
must repeat plan and latency analysis with its own retention, skew, pool,
database settings and network.

## Validation status

Offline validation in the audit sandbox completed the following checks:

- all 820 Java sources parse, the full core and JDBC production trees compile
  with Java 17, and the corrected qualification support plus its regression test
  compile against them;
- an isolated harness renders the amended Markdown evidence and replays the
  PostgreSQL, MySQL and Oracle plan-choice cases reported by the connected run;
- all four migration resources contain the five complete ordered indexes;
- workflow YAML, repository-local documentation links, ADR identifiers,
  trailing whitespace and final newlines pass equivalent static checks;
- the changed public migrator keeps its phase-14 public ABI;
- the changed JDBC Gradle script has no Groovy lint finding. The repository-wide
  lint run still reports one unrelated pre-existing `UnusedPrivateMethod` in
  `XmlAssemblyLineGeneratorPluginTest.groovy`.

The first connected execution supplied the three optimizer-choice examples
documented above. The correction cannot be rerun here because Docker is absent
and the Gradle distribution cannot be downloaded in this sandbox. Phase closure
therefore still requires four green CI artifacts:

```text
postgresql.md
mysql.md
mariadb.md
oracle.md
```

Run locally with:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-jdbc:integrationTest
./gradlew check
```

Or diagnose one engine with:

```bash
./gradlew :gear4jtest-jdbc:integrationTest -Pgear4jDatabaseDialect=postgresql
```

This phase does not introduce or require dependency lockfiles, Gradle
verification metadata or other post-1.0 supply-chain controls.

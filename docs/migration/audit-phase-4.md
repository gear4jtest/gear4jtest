# Applying the 7 September audit's phase 4 delivery

| Field | Value |
| --- | --- |
| Status | Implemented — connected validation pending |
| Owner | Gear4J maintainers |
| Last reviewed | 2026-09-12 |

The phase 4 source archive is cumulative from phase 3, including the phase 2
Gradle closure correction. Java 17, Gradle 9.6.1, artifact/package names, V1
schemas and the pre-1.0 dependency policy are preserved.

## Deleted source files

The following unused classes are intentionally absent:

```text
gear4jtest-core/src/main/java/io/github/gear4jtest/core/exception/AssemblyLineException.java
gear4jtest-jdbc/src/main/java/io/github/gear4jtest/jdbc/persistence/JdbcRepositoryTransaction.java
```

Extracting the ZIP into a clean directory reflects these removals. Copying ZIP
contents over an existing repository cannot delete files that are absent from
the archive. In that case, after copying the sources, apply the deletion-only
patch from the repository root:

```bash
git apply --check docs/audit/evidence/remediation-2026-09-12-phase-4-removals.patch
git apply docs/audit/evidence/remediation-2026-09-12-phase-4-removals.patch
```

Only use the patch if both obsolete files still exist. If one is already gone or
either was locally modified, review the two paths individually instead. The
patch targets only these source files. `verifyRetiredAuditSources`, included in
`check` and `releaseMetadataCheck`, rejects their accidental reintroduction.

`AssemblyLineException` was public but unused in the supplied project. External
code that still imports it must migrate before recompiling this pre-1.0 version;
use `ExecutionResult` and its error contract for execution failures. The JDBC
helper was package-private; active `JdbcTransactionOperations` is unchanged.

## Exception logging

Framework exception logs now contain the exception class and a withheld-details
indication by default. Exception messages, stacks, causes and suppressed
exceptions require `-Dgear4j.logging.includeExceptionDetails=true` on the
application JVM. This is independent from Boot persistence properties and from
logger levels. Original failures in execution results and traces are preserved.
See [logging policy](../architecture/logging.md).

## Store lifecycle

Provider constructors and close callbacks can now run concurrently for different
configuration keys. Custom plugins must support that concurrency and avoid
cyclic/reentrant same-key acquisition. An interrupted waiter exits without
cancelling the shared construction. Stop all consumers before closing a shared
provider. See [the lease ADR](../decisions/0043-artifact-stores-use-provider-leases-and-directory-scoped-spool-quotas.md)
for invalidation, close races and shared-identity behavior.

## Validation

Run the commands and retain the evidence listed in the
[phase 4 report](../audit/remediation-2026-09-12-phase-4-diagnostics-contention.md).
The local probe results are not a substitute for the actual Gradle/JUnit,
Logback, database, SCA and release checks.

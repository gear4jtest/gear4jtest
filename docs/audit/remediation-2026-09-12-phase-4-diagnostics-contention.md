# 7 September audit — phase 4: diagnostics, contention and qualification

| Field | Value |
| --- | --- |
| Status | F07/F08/F15 implemented — connected release qualification pending |
| Owner | Gear4J maintainers |
| Last reviewed | 2026-09-12 |

This cumulative delivery starts from `gear4jtest-20260912-phase-3.zip`, SHA-256
`c8ab2e5c0a2042c6e33a8f7890fef3a5a202265e532191966f04197020f6cd9d`.
It preserves phases 1–3 and the phase 2 `restrictedCapabilities()` Gradle
closure fix. The repeated original attachment is not used to overwrite those
already delivered corrections.

The implementation portion of all four audit phases is complete. **The release
qualification portion is not complete.** No new vulnerability-scan, database,
JMH, JUnit, real Logback or complete Gradle result is available from this phase.

## Findings addressed

### F07 — protect exception logs separately from captured values

**Locations:** `gear4jtest-core/.../util/ExceptionDiagnostics.java`; 35 exception
arguments across engine/lifecycle/event logging, JDBC flush diagnostics,
external loading/artifact cleanup, experimental cache and XML formatter logging.

The protected default gives SLF4J a separate throwable containing the original
exception class name and a withheld-details indication. It has no message copied
from the failure, no original stack, cause or suppressed graph, and no retained
reference to the original failure. User-defined message/rendering methods are
not inspected. The normal result/trace/throwable path still receives the original
exception according to its existing normalization contract.

Full exception details require the explicit JVM property
`gear4j.logging.includeExceptionDetails=true`. Absent or invalid values and denied
property reads retain protection. The setting is process-wide, read per log call,
and independent from logger levels, Boot persistence redaction and event payload
policies. No runtime logging backend or other dependency is added.

This prevents the audited exception-graph path. It does not scrub application or
dependency logs, arbitrary structured identifiers or MDC. Diagnostic access and
retention still belong to the host application. Default logs lose full-stack
diagnosis; that is the deliberate confidentiality tradeoff. See
[logging policy](../architecture/logging.md) and
[ADR 0046](../decisions/0046-exception-logs-require-explicit-detail-opt-in.md).

### F08 — move backend callbacks outside shared metadata monitors

**Locations:** `gear4jtest-external-api/.../AssemblyLineStoreResolver.java` and
`.../storage/DefaultArtifactStoreProvider.java`; plugin concurrency Javadoc in
`.../spi/ArtifactStorePlugin.java`.

The resolver shares pending construction by assembly-line id. The provider
shares it by store type/property map. Construction, provider release and store
close run outside the global metadata monitors. Cache/reference-count changes
remain synchronized. A blocked backend no longer holds the monitor needed for
an independent cache hit or resolver statistics snapshot.

The phase 1 ownership rules remain: every provider acquisition is returned,
active operations retain evicted/replaced entries, identity sharing does not
collapse separate acquisitions, and caller-owned resources are not closed by
default no-op providers.

Additional race handling:

- Same-key callers share a pending construction and observe its failure. A later
  invocation can retry. Interrupted waiters preserve the interrupt flag without
  cancelling another caller's construction.
- Invalidating a key during construction prevents late cache installation. The
  original caller may hold the uncached operation lease until its work finishes.
- Closing the resolver/provider rejects new admission and wakes pending waiters.
  It does not wait for plugin constructors: their late results are disposed when
  they return. Active provider consumers must still stop before provider close.
- Same-provider-key reconstruction waits for final backend closure. Shared store
  identities retired during overlapping construction are rejected if returned
  late and are not closed a second time. Weak identity markers avoid retaining
  unrelated closed backends behind a permanently blocked constructor.
- Same-thread recursive acquisition of a pending key fails explicitly. Cross-key
  or cross-thread cyclic plugin dependencies are not automatically detected.
- Cleanup attempts every retired store and retains secondary failures; a fatal
  cleanup Error takes precedence over an ordinary cleanup exception. Partial
  primary/fallback construction also preserves all cleanup failures.

This is not a backend timeout mechanism: the thread executing a constructor or
close can still block in plugin I/O. The correction isolates that wait from
unrelated cache metadata. See the updated
[store lease ADR](../decisions/0043-artifact-stores-use-provider-leases-and-directory-scoped-spool-quotas.md).

### F15 — reconcile removals, module ownership and delivered sources

**Locations:** root/core `AGENTS.md`, `build.gradle`, audit roadmaps, JDBC
architecture and Boot README; the two removed Java sources below.

No production/test Java reference to either class was found outside its own
declaration. The delivery removes:

```text
gear4jtest-core/src/main/java/io/github/gear4jtest/core/exception/AssemblyLineException.java
gear4jtest-jdbc/src/main/java/io/github/gear4jtest/jdbc/persistence/JdbcRepositoryTransaction.java
```

`verifyRetiredAuditSources` guards these paths in `check` and
`releaseMetadataCheck`. Root module instructions now cover every included module;
the core instructions correctly place JDBC and its JSON serialization outside
core. Historical removal claims explicitly acknowledge that the later input
archive still contained the files. JDBC baseline documentation now matches the
existing opt-in and minimum-presence validation; matrix fixtures are separated
from actual candidate qualification.

**ZIP overlays cannot perform deletions.** The
[migration instructions](../migration/audit-phase-4.md) include a
[deletion-only patch](evidence/remediation-2026-09-12-phase-4-removals.patch).
Use it if copying the ZIP over a repository that still contains both retired
files. A clean extraction already reflects the deletions. The supplied archive
contains no Git repository metadata or transient build caches.

`AssemblyLineException` was public: any external consumer still importing it must
adapt before recompiling this pre-1.0 version. The active JDBC transaction helper
and V1 schemas are unchanged. No rename, dependency locking or verification
metadata is introduced.

## Added regression sources

| Suite | New methods | Declared invocations | Main assertions |
| --- | --- | --- | --- |
| `AssemblyLineStoreResolverConcurrencyTest` | 6 | 7 | Independent hit during build/release, invalidation race, close and fatal late cleanup, failure fan-out/retry, interrupted waiter |
| `DefaultArtifactStoreProviderConcurrencyTest` | 8 | 9 | Single construction with balanced leases, slow close and same-key recreation, late close, retired shared aliases, fatal failure/retry, all-store cleanup, interruption and partial-build cleanup |
| `ExceptionLoggingPolicyTest` | 3 | 9 | Actual Logback rendering of run/station hook failures, preservation of consumer causes, explicit detail opt-in, absent/invalid values and hostile message methods |
| **Total** | **17** | **25** | Existing phase 1/3 tests remain in the cumulative tree |

These JUnit sources were authored but were not compiled or executed with their
actual dependencies here. The Logback test explicitly checks message, cause,
suppressed-exception and stack-frame sentinels; the standalone logging probe
below uses a temporary capture adapter and does not establish backend behavior.

## Observed validation

[Machine-readable phase 4 evidence](evidence/remediation-2026-09-12-phase-4-validation.json)
records commands, outputs, compilation scope and limitations.

- Java 17 compilation of the core production sources and the selected external
  store/loading source closure succeeds with temporary SLF4J/JSpecify stand-ins.
  This is not a build of all modules against the real dependency graph.
- **17 new store probe scenarios pass**, covering construction/close contention,
  same-key coordination, invalidation and close races, shared identities,
  interruption, recursive acquisition and failure cleanup.
- **12 phase 1 lease scenarios pass again**, including eviction during stream
  reading, same-identity replacement, concurrent initialization, publication and
  generated loading with the JDK compiler. This checks that the contention
  refactor preserves the earlier ownership fixes.
- **10 logging capture probe scenarios pass** against the production classes
  with a temporary SLF4J capture adapter. Original result causes remain reachable;
  protected logger arguments do not retain the original throwable graph.
- **9 Python release-tool tests pass**, and the two release shell scripts pass
  Bash syntax checking.
- Focused Gradle tests, `spotlessApply`, and `check releaseMetadataCheck` all stop
  before build configuration while downloading Gradle 9.6.1:
  `Network is unreachable`. No JUnit/Logback/Testcontainers/Spotless result is
  inferred from the standalone probes.
- Local metadata, Markdown-link, ADR, retired-source, deletion-patch and archive
  checks are retained with the final evidence. They do not replace Gradle gates.

The independent-hit timing samples demonstrate progress while another backend
is held behind a barrier. They are not p95/p99, throughput, maximum-volume or
production-capacity measurements.

## Remaining qualification

| Gate | Required evidence | Current state |
| --- | --- | --- |
| Actual compilation, JUnit and Logback | Focused suites and full Gradle reports on this tree | Blocked before Gradle startup |
| Formatting and source architecture | `spotlessApply`, `check`, `releaseMetadataCheck` | Gradle execution blocked; local source/document checks only |
| Coverage | JaCoCo reports and existing ratchets | Not run; thresholds unchanged |
| JDBC matrix and plans | Current PostgreSQL/MySQL/MariaDB/Oracle reports, including prior-phase publication and migration regressions | Not run |
| Large traces, churn and slow DB | Representative memory/latency measurements plus versioned JMH budgets | Not measured; no capacity claim |
| Dependency vulnerabilities | Fresh SCA result with configured NVD feed | Not run; dependencies unchanged by this phase |
| Published-consumer compatibility | Boot, Gradle TestKit and staged consumer execution | Not run |
| Staging and reproducibility | Staged artifacts and two-build hash comparison | Not run; no publication attempted |

First run with Java 17 and dependency access:

```bash
./gradlew --no-daemon :gear4jtest-core:test :gear4jtest-external-api:test :gear4jtest-jdbc:test :gear4jtest-experimental-cache:test :gear4jtest-xml:test
./gradlew --no-daemon spotlessApply
./gradlew --no-daemon check releaseMetadataCheck
./gradlew --no-daemon :gear4jtest-jdbc:integrationTest :gear4jtest-external-jdbc:integrationTest
./gradlew --no-daemon coverageReport verifyPerformanceBudgets
```

Then follow [release qualification](../releasing.md) with Docker, an approved NVD
feed, the actual candidate version and retained reports. The existing
`releaseCheck stageMavenCentral` validates/stages locally; deployment is separate.
An earlier source archive or stale report does not qualify this candidate.

## Commit message

```text
fix(runtime): protect exception logs and reduce store contention
```

The [roadmap](../roadmap/audit-remediation-2026-09-07.md) now records all four
implementation phases. Qualification remains an explicit release blocker, not
a fifth implementation phase silently considered complete.

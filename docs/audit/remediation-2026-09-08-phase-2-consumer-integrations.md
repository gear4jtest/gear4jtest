# 7 September audit — phase 2 publication and consumer integrations

Status: **Implemented; Gradle and database qualification pending**.
Reviewed: 2026-09-08. Owner: Gear4J maintainers.

This is a cumulative source delivery based on gear4jtest-20260907-phase-1.zip
(SHA-256 da57d02c4b1c2abc75b2554991b195c7e31e404f7b022a244f3dc4233ce74bd0).
Phase 1 changes are preserved. Findings refer to the 7 September audit and the
[four-phase remediation plan](../roadmap/audit-remediation-2026-09-07.md).

## Living-documentation build failure

The roadmap added in phase 1 omitted the exact metadata table required by
verifyLivingDocumentationMetadata. It now includes Status, Owner and Last reviewed
with a valid 2026-09-08 review date. The build task and its validation rules were
not weakened. A local replay of its field/date rules passes for all ten documents
in its scope; execution of the actual Gradle task is still blocked at the wrapper.

## F09 — publication-stage concurrency

Changed classes in gear4jtest-external-jdbc:

- OperationChainObjectRepositoryJdbc: commit reads the stage under a row lock;
  renewal checks the stored identity under the same lock. A duplicate insert
  followed by a missing stage retries up to three times. Persistent churn becomes
  a SQLTransactionRollbackException with SQLState 40001, retained as the cause of
  the repository exception.
- OperationChainPublicationStageJdbcOperations: findForUpdate reads stage metadata
  before locking/reading its tags. The transaction retains the row lock through
  publication. Missing stages remain idempotent commit no-ops.
- OperationChainTagJdbcOperations: stage-tag reads use FOR UPDATE, including after
  waiting for renewal, instead of a potentially older consistent-read snapshot.

Abort and commit delete the parent stage first. All five existing V1 schemas
already declare ON DELETE CASCADE for stage tags. This removes the former
child-before-parent deletion order without changing a schema or adding a V2
migration. Conditional abort keeps its atomic revision predicate.

PublicationConcurrencyChecks adds four real-connection interleavings:

1. a commit waits for an uncommitted renewal and publishes the renewed tags;
2. a renewal waits for commit and its subsequently accepted tags survive;
3. abort waits for the committing transaction;
4. a competing commit waits and completes idempotently.

The checks are wired into the H2 integration test and the existing PostgreSQL,
MySQL, MariaDB and Oracle matrix. JDBC proxies in this fixture only coordinate
execution; SQL and transactions still go to the real database. The fixture uses
bounded latches, futures and statement timeouts. These database tests were added
but could not be run in this environment.

The bounded staging loop handles one identified disappearance race. It is not a
blanket retry policy for deadlocks, connection failures or uncertain commits.

## F12 — actual Spring Boot bean producers

Gear4jAutoConfiguration now declares afterName dependencies on:

- DataSourceAutoConfiguration;
- DataSourceTransactionManagerAutoConfiguration;
- CompositeMeterRegistryAutoConfiguration;
- SimpleMetricsExportAutoConfiguration.

This makes conditions evaluate after the relevant bean definitions. Class-name
ordering keeps the optional Actuator boundary. Existing datasource qualifiers,
user-bean overrides, metrics flags and persistence opt-in behavior remain in use.

Gear4jBootConsumerTest adds three cases: real Boot-created datasource, JDBC
transaction manager and registry with Gear4J persistence/metrics/health;
disabled Gear4J integrations with Boot infrastructure present; and startup without
Actuator classes. Existing explicit-bean tests remain.

spring-boot-actuator-autoconfigure is added only to testImplementation, using the
existing Boot version in the catalog. No dependency version, runtime Actuator
requirement, dependency locking or verification metadata is introduced.

The choice follows Boot's documented ordering: [SimpleMetricsExportAutoConfiguration](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/actuate/autoconfigure/metrics/export/simple/SimpleMetricsExportAutoConfiguration.html)
is a fallback after other registry exporters and precedes
[CompositeMeterRegistryAutoConfiguration](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/actuate/autoconfigure/metrics/CompositeMeterRegistryAutoConfiguration.html).

## F13 — operator classpath and clean generation

XmlAssemblyLineGenerateTask exposes operatorClasspath as an @Classpath input.
The task opens a URLClassLoader with the plugin's translator loader as parent,
sets the context loader while constructing and using the translator, then
restores the previous loader and closes the new loader on success or failure.
Only generated strings leave this scope. Existing restricted capability checks
still apply; classpath access does not authorize an operator.

XmlAssemblyLineGeneratorPlugin supplies the Java consumer's compile dependencies
and the independent gear4jOperators source set. Java operators in the same project
can live under src/gear4jOperators/java and compile before XML generation; main
Java compilation follows generation. Operator output is available to main and
test code and is included in jar/sourcesJar. Operators must not depend on main
output or generated pipelines. The [plugin README](../../gear4jtest-gradle-xml2java/README.md)
documents this source layout and explicit additional classpaths.

Generated sources are registered using the task output provider so dependent
tasks, including sourcesJar, retain the generation dependency.

Three new TestKit consumers cover an external operator JAR and bytecode-only
classpath invalidation, clean build/packaging of same-project operators, and
preservation of previous output when an operator disappears. One additional
unit test checks context-loader restoration on failure; the existing successful
generation test checks restoration too. Existing configuration/build-cache tests
remain. These tests are present, not reported as executed.

The input annotation follows Gradle's [Classpath contract](https://docs.gradle.org/current/javadoc/org/gradle/api/tasks/Classpath.html).
The project still targets Gradle 9.6.1 and Java 17.

## Validation record

| Check | Result | Practical scope |
| --- | --- | --- |
| Living-documentation metadata replay | Passed: 10 documents | Exact required fields and valid ISO review dates; actual Gradle task not run. |
| Java 17 JDBC compilation | Passed | Three modified JDBC production classes and their source dependency closure, against the previously compiled core/external API. SLF4J/JSpecify stand-ins from phase 1 are present on that local classpath. |
| Local SQL protocol probes | Passed: 5 scenarios | Production repository code with JDBC doubles: lock-query order, absent commit, parent deletion, retry after disappearance and bounded rollback failure. This does not validate database locking. |
| Focused Gradle tests and metadata task | Blocked, exit 1 | Wrapper could not download Gradle; no JUnit, Boot, TestKit or database test executed. |
| spotlessApply | Blocked, exit 1 | Same download failure; formatting conformity unverified. |
| check | Blocked, exit 1 | Same failure; aggregate build/static-analysis gates unverified. |
| git diff --check | Passed | Whitespace only; not a substitute for the formatter. |

The wrapper reports Network is unreachable while downloading
https://services.gradle.org/distributions/gradle-9.6.1-bin.zip. No compilation or
test failure from a started Gradle build is hidden by that result. Boot/Groovy
sources have not been compiled against the real Gradle dependency graph here.

Raw outcomes are recorded in [phase 2 validation results](evidence/remediation-2026-09-08-phase-2-validation.json).

Run the following in the normal development/CI environment, with Docker for the
database matrix:

    bash gradlew --no-daemon verifyLivingDocumentationMetadata :gear4jtest-external-jdbc:integrationTest --tests '*OperationChainPublicationRepositoryJdbcIT' --tests '*ExternalJdbcMultiDialectIT' :gear4jtest-spring-boot-starter:test --tests '*Gear4j*Test' :gear4jtest-gradle-xml2java:test --tests '*XmlAssemblyLine*Test'
    bash gradlew --no-daemon spotlessApply
    bash gradlew --no-daemon check

The default matrix selection is all. CI may select a specific supported database
with -Pgear4jDatabaseDialect=postgresql (or mysql, mariadb, oracle). A Docker-skipped
matrix does not qualify the database changes.

## Commit message

    fix(integration): serialize publication and qualify Boot and Gradle consumers

    Lock staged publication metadata and tags before consumption or renewal.
    Order Boot integration after datasource, transaction and meter-registry producers.
    Track operator bytecode inputs and compile same-project operators before XML generation.
    Add database and consumer regressions; restore required roadmap metadata.

Phases 3 and 4 remain planned. This delivery is not a release-ready build.

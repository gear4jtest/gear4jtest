# Migrate a pre-1.0 application to the Gear4J 1.0 surface

This guide covers the source-level boundary finalized for Gear4J 1.0. It is intended for applications built from an
earlier development snapshot. It does not invent aliases for internal implementation classes: migrate application code
to the public facade and stable package listed here.

## 1. Inventory implementation imports

Start with application production sources, generated sources and test fixtures:

```bash
rg -n 'io\.github\.gear4jtest\.(core\.(engine|execution|internal|util)|external\.api\.storage|jdbc\.migration|xml\.(generator|model|parser))' \
  src build/generated
```

Review every match rather than replacing package prefixes mechanically. Some internal types have no public equivalent
because applications should no longer own that runtime state.

The compatibility-sensitive categories are documented in
[API, SPI and internal contracts](../architecture/api-contracts.md). Public types are application-facing; SPI types are
implemented by applications or integrations. `@Internal` and `@Experimental` types are outside the 1.x compatibility
promise unless a release note explicitly promotes them.

## 2. Use the execution facade

Replace application references to `io.github.gear4jtest.core.engine.AssemblyLineEngine` with the public
`AssemblyLineExecutor` interface. Construct the default implementation through `AssemblyLineExecutors`, or inject the
facade from Spring.

```java
AssemblyLineExecutor executor = AssemblyLineExecutors.builder()
        .resourceFactory(resourceFactory)
        .build();

RunRequest<Input> request = RunRequest.builder()
        .input(input)
        .context(context)
        .build();

ExecutionResult<Output> result = executor.execute(pipeline, request);
```

Move per-run input, context, resource overrides, persistence overrides, cancellation and extensions into `RunRequest`.
Keep pipeline identity, stations, defaults and default runtime configuration on `AssemblyLine`. Read execution state
through `ExecutionResult`, `RunTrace` and `StationTrace`; do not cast those views to mutable execution records.

Spring applications should inject `AssemblyLineExecutor`. Executor customization uses
`Gear4jAssemblyLineExecutorCustomizer`, while operators and runtime extensions remain ordinary Spring beans.

## 3. Replace promoted package copies

The temporary aliases below were removed before 1.0. All replacements are provider-neutral contracts in the core
artifact.

| Removed type | 1.0 replacement |
| --- | --- |
| `core.execution.AssemblyRunManager` | `core.persistence.RunPersistenceManager` |
| `core.execution.PersistenceOperationalStatus` | `core.persistence.PersistenceOperationalStatus` |
| `core.execution.PersistenceRuntimeMonitor` | `core.persistence.PersistenceRuntimeMonitor` |
| `core.execution.PersistenceRuntimeStats` | `core.persistence.PersistenceRuntimeStats` |
| `core.engine.support.WorkerConcurrencyConfiguration` | `core.api.config.WorkerConcurrencyConfiguration` |
| `core.engine.support.WorkerConcurrencyPolicy` | `core.api.config.WorkerConcurrencyPolicy` |
| `core.engine.support.WorkerConcurrencyRegistryConfiguration` | `core.api.config.WorkerConcurrencyRegistryConfiguration` |
| `core.engine.support.WorkerLockAcquisitionPolicy` | `core.api.config.WorkerLockAcquisitionPolicy` |
| `core.engine.support.WorkerConcurrencyStrategy` | No direct replacement; combine `WorkerConcurrencyPolicy` with `WorkerLockAcquisitionPolicy` |

Every abbreviated package in the table starts with `io.github.gear4jtest.`. The release gate scans both the binary and
source core JARs and fails if one of these removed paths returns.

## 4. Select explicit published modules

Declare the artifact that owns the feature used by the application. Do not depend on an aggregator or import internal
classes from another module.

| Application need | Published artifact or plugin |
| --- | --- |
| Java DSL, executor, runtime and extension SPI | `gear4jtest-core` |
| Jackson payload cloning | `gear4jtest-jackson` |
| JDBC execution persistence | `gear4jtest-jdbc` |
| Provider-neutral external definitions | `gear4jtest-external-api` |
| JDBC external-definition repositories | `gear4jtest-external-jdbc` |
| XML validation and translation | `gear4jtest-xml` |
| Plain Spring integration | `gear4jtest-spring` |
| Spring Boot auto-configuration | `gear4jtest-spring-boot-starter` |
| Micrometer meters | `gear4jtest-micrometer` |
| XML source generation | plugin `io.github.gear4jtest.xml2java` |

All artifact coordinates use the `io.github.gear4jtest` group. The legacy plugin id
`io.github.gear4jtest.gradle.xml2java` remains compatible during 1.x, but new builds should use the canonical id above.

## 5. Recheck operational migrations

Java compilation is only one part of the upgrade. Before deployment:

- compare existing `gear4j.*` properties with the starter property table;
- apply committed JDBC migrations for the selected dialect without editing released migration files;
- keep restricted XML on GEL and register explicit TEST/RUN operator capabilities;
- review metric names and bounded tags before changing dashboards or alerts;
- choose an explicit persistence redaction policy before retaining payloads, context, results or error messages;
- retain the best-effort event-delivery assumption unless the application owns a separate durable transport.

See the [compatibility policy](../compatibility-policy.md),
[JDBC migration architecture](../architecture/jdbc-migrations.md),
[XML security boundary](../architecture/xml-security.md) and
[Micrometer observability](../architecture/micrometer-observability.md) for those contracts.

## 6. Qualify the migrated consumer

Run the application build on Java 17, then exercise representative success, failure, skip, stop and cancellation paths.
Library maintainers additionally stage the full Gear4J publication and run its autonomous consumer:

```bash
./gradlew clean build
./gradlew stageMavenCentral consumerSmokeTest -PprojectVersion=1.0.0
```

For upgrades after the first stable release, configure the immediately preceding stable baseline and run the binary and
source compatibility gate:

```bash
./gradlew apiCompatibilityCheck -Pgear4j.apiBaselineVersion=<previous-stable-version>
```

The [compile-backed getting-started example](../../config/consumer-smoke/src/main/java/io/github/gear4jtest/consumer/GettingStartedExample.java)
is the smallest executable reference for the finalized facade.

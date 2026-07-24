# gear4jtest-spring-boot-starter

Spring Boot auto-configuration for Gear4J.

The starter imports the plain Spring integration, validates `gear4j.*` properties,
can wire JDBC persistence when explicitly enabled, and automatically adds
Micrometer runtime metrics when a `MeterRegistry` bean is available.

When Spring Boot Actuator is present and JDBC persistence is enabled, the starter
also contributes separate persistence liveness and readiness indicators.

## Main properties

| Property | Type | Default | Description |
|---|---:|---:|---|
| `gear4j.parallel.default-await-timeout` | `Duration` | `30s` | Default timeout used by parallel branch execution. |
| `gear4j.persistence.enabled` | `boolean` | `false` | Enables JDBC persistence for runs and station logs. |
| `gear4j.persistence.dialect` | `Gear4jDatabaseDialect` | — | Required when JDBC persistence is enabled. Supported values follow the core `Gear4jDatabaseDialect` enum. |
| `gear4j.persistence.auto-create-tables` | `boolean` | `false` | Lets Gear4J run its internal JDBC schema migrations automatically. Keep `false` when the application manages migrations explicitly. |
| `gear4j.persistence.baseline-on-migrate` | `boolean` | `false` | Explicitly adopts a verified compatible Gear4J schema without migration history. V1 tables, columns and named indexes are validated first. |
| `gear4j.persistence.batch-size` | `int` | `500` | Number of station logs flushed per persistence batch. |
| `gear4j.persistence.max-pending-logs-per-run` | `int` | `10000` | Backpressure guard for buffered station logs per active run. Must be greater than or equal to `batch-size`. |
| `gear4j.persistence.flush-threads` | `int` | `1` | Number of worker threads used for asynchronous JDBC station-log flushes. |
| `gear4j.persistence.max-scheduled-flush-tasks` | `int` | `1000` | Maximum queued asynchronous flush tasks before persistence fails fast with backpressure. |
| `gear4j.persistence.flush-interval` | `Duration` | `1s` | Periodic flush interval for pending station logs. |
| `gear4j.persistence.shutdown-timeout` | `Duration` | `30s` | Maximum wait during persistence manager shutdown. |
| `gear4j.persistence.shutdown-retry-initial-backoff` | `Duration` | `100ms` | Initial delay between failed shutdown flush attempts. |
| `gear4j.persistence.shutdown-retry-max-backoff` | `Duration` | `2s` | Maximum exponential backoff between shutdown flush attempts. |
| `gear4j.persistence.jdbc-statement-timeout` | `Duration` | `30s` | JDBC statement query timeout applied to Gear4J persistence statements; use `0` to disable. |
| `gear4j.persistence.readiness-max-buffered-station-logs` | `int` | `5000` | Readiness becomes `DOWN` above this current backlog size. |
| `gear4j.persistence.readiness-max-backlog-age` | `Duration` | `30s` | Readiness becomes `DOWN` when the oldest buffered log exceeds this age. |
| `gear4j.persistence.connectivity-probe-timeout` | `Duration` | `2s` | Timeout for the provider-specific readiness connectivity query. |
| `gear4j.persistence.redaction-mode` | `DISCARD` / `REQUIRE` / `DISABLED` / `WARN` | `DISCARD` | Controls sensitive-value handling when persistence is enabled without a `SensitiveDataRedactor` bean. `WARN` is deprecated compatibility behavior. |
| `gear4j.metrics.enabled` | `boolean` | `true` | Enables Micrometer integration when a `MeterRegistry` bean is available. |

## JDBC persistence examples

Let Gear4J create or migrate its internal schema during startup:

```properties
gear4j.persistence.enabled=true
gear4j.persistence.dialect=POSTGRESQL
gear4j.persistence.auto-create-tables=true
```

An existing schema without `gear4j_schema_history` is rejected by default. Only
after verifying that it matches the bundled V1 schema, opt in explicitly:

```properties
gear4j.persistence.baseline-on-migrate=true
```

For production deployments that manage DDL outside the application, keep
auto-creation disabled and apply the SQL migrations from the core module for the
selected dialect. The default `DISCARD` mode stores only metadata: contexts are
empty and payloads, results and error messages are removed. Applications that
need selected persisted values should provide a `SensitiveDataRedactor` bean.
Set `gear4j.persistence.redaction-mode=REQUIRE` when startup must fail unless
that bean is available:

```properties
gear4j.persistence.enabled=true
gear4j.persistence.dialect=POSTGRESQL
gear4j.persistence.auto-create-tables=false
gear4j.persistence.redaction-mode=REQUIRE
```

When an application `ObjectMapper` bean is available, the starter reuses it for
JDBC persistence so registered Java time modules, custom serializers and
business types remain consistent with the rest of the application. A
`PersistenceJsonCodec` bean takes precedence when persistence requires a
dedicated serialization strategy.


## Redaction migration

Before this change, the starter defaulted to `WARN`, which persisted raw values
when no `SensitiveDataRedactor` bean was available. The default is now
`DISCARD`. Applications that intentionally depend on complete raw persistence
must opt in explicitly with:

```properties
gear4j.persistence.redaction-mode=DISABLED
```

`WARN` remains temporarily available as a deprecated compatibility mode. It
also permits raw capture but emits the persistence manager warning. New
configurations should use `DISCARD`, `REQUIRE` or `DISABLED` according to their
intended security posture.

## Actuator probes

`gear4jPersistenceLivenessIndicator` is process-local and never calls the
database. `gear4jPersistenceReadinessIndicator` verifies current database
connectivity and persistence backlog recovery. The legacy
`gear4jPersistenceHealthIndicator` bean name aliases readiness.

The connectivity probe timeout bounds the validation statement. Configure a
finite connection-acquisition timeout on the application datasource/pool as
well, because JDBC does not expose a portable per-call timeout for
`DataSource#getConnection()`.

Keep liveness and readiness in separate Actuator health groups. Cumulative
failed-flush and rejected-append counters remain observable but do not make a
recovered runtime permanently unhealthy.

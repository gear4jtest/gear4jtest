# gear4jtest-spring-boot-starter

Spring Boot auto-configuration for Gear4J.

The starter imports the plain Spring integration, validates `gear4j.*` properties,
can wire JDBC persistence when explicitly enabled, and automatically adds
Micrometer runtime metrics when a `MeterRegistry` bean is available.

When Spring Boot Actuator is present and JDBC persistence is enabled, the starter
also contributes a `gear4jPersistenceHealthIndicator` bean exposing persistence
buffer and flush statistics.

## Main properties

| Property | Type | Default | Description |
|---|---:|---:|---|
| `gear4j.parallel.default-await-timeout` | `Duration` | `30s` | Default timeout used by parallel branch execution. |
| `gear4j.persistence.enabled` | `boolean` | `false` | Enables JDBC persistence for runs and station logs. |
| `gear4j.persistence.dialect` | `Gear4jDatabaseDialect` | — | Required when JDBC persistence is enabled. Supported values follow the core `Gear4jDatabaseDialect` enum. |
| `gear4j.persistence.auto-create-tables` | `boolean` | `false` | Lets Gear4J run its internal JDBC schema migrations automatically. Keep `false` when the application manages migrations explicitly. |
| `gear4j.persistence.batch-size` | `int` | `500` | Number of station logs flushed per persistence batch. |
| `gear4j.persistence.max-pending-logs-per-run` | `int` | `10000` | Backpressure guard for buffered station logs per active run. Must be greater than or equal to `batch-size`. |
| `gear4j.persistence.flush-threads` | `int` | `1` | Number of worker threads used for asynchronous JDBC station-log flushes. |
| `gear4j.persistence.flush-interval` | `Duration` | `1s` | Periodic flush interval for pending station logs. |
| `gear4j.persistence.shutdown-timeout` | `Duration` | `30s` | Maximum wait during persistence manager shutdown. |
| `gear4j.metrics.enabled` | `boolean` | `true` | Enables Micrometer integration when a `MeterRegistry` bean is available. |

## JDBC persistence examples

Let Gear4J create or migrate its internal schema during startup:

```properties
gear4j.persistence.enabled=true
gear4j.persistence.dialect=POSTGRESQL
gear4j.persistence.auto-create-tables=true
```

For production deployments that manage DDL outside the application, keep
auto-creation disabled and apply the SQL migrations from the core module for the
selected dialect:

```properties
gear4j.persistence.enabled=true
gear4j.persistence.dialect=POSTGRESQL
gear4j.persistence.auto-create-tables=false
```

# Spring Boot starter

The `gear4jtest-spring-boot-starter` module imports the plain Spring integration
and exposes validated properties.

Example:

```yaml
gear4j:
  parallel:
    default-await-timeout: 30s
  persistence:
    enabled: true
    dialect: POSTGRESQL
    auto-create-tables: false
    batch-size: 500
    max-pending-logs-per-run: 10000
    flush-threads: 1
    max-scheduled-flush-tasks: 1000
    flush-interval: 1s
    shutdown-timeout: 30s
    jdbc-statement-timeout: 30s # use 0 to disable Statement#setQueryTimeout
    readiness-max-buffered-station-logs: 5000
    readiness-max-backlog-age: 30s
    connectivity-probe-timeout: 2s
    redaction-mode: WARN # WARN | REQUIRE | DISABLED
  metrics:
    enabled: true
```

Persistence is opt-in. When enabled, `gear4j.persistence.dialect` is mandatory.
Gear4J never auto-detects the database dialect.

`gear4j.persistence.redaction-mode` defaults to `WARN`. In this mode, Gear4J
starts without a `SensitiveDataRedactor` but logs that payloads, contexts and
results are persisted as-is. Set it to `REQUIRE` in production environments that
must fail fast when no redactor bean is available. `DISABLED` is an explicit
opt-out for trusted/test deployments that deliberately persist raw values without
emitting the missing-redactor startup warning.

`gear4j.persistence.auto-create-tables` defaults to `false`. With the default,
Gear4J expects the core schema to already exist, typically because the host
application applied the SQL resources through Flyway, Liquibase or another
migration process. Set it to `true` only when Gear4J should create and migrate
its own internal schema at startup.


`gear4j.persistence.flush-threads` defaults to `1`, which keeps JDBC persistence
ordering simple and conservative. Higher-throughput applications may increase it
when the database and repository implementation can absorb concurrent batch
flushes.

`gear4j.persistence.jdbc-statement-timeout` defaults to `30s` and is applied to
Gear4J JDBC persistence statements through `Statement#setQueryTimeout`. Set it to
`0` only when the datasource, driver or infrastructure already enforces a
statement/query timeout.

## Actuator health

If Spring Boot Actuator is on the classpath and JDBC persistence is enabled, the
starter contributes separate liveness and readiness indicators:

- `gear4jPersistenceLivenessIndicator` checks only that the persistence runtime
  has not shut down. It never queries the database;
- `gear4jPersistenceReadinessIndicator` executes a provider-specific bounded
  connectivity query and evaluates current backlog size, age and recovery state;
- `gear4jPersistenceHealthIndicator` remains an alias of the readiness bean for
  compatibility.

`connectivity-probe-timeout` is applied through JDBC
`Statement#setQueryTimeout` after a connection is acquired. The host application
must also configure a finite datasource/pool connection-acquisition timeout;
JDBC has no portable per-call timeout for `DataSource#getConnection()`.

A historical flush failure or rejected append remains visible in metrics and
health details, but does not keep readiness permanently `DOWN`. Readiness returns
to `UP` once connectivity is available and a successful flush has recovered the
pending backlog.

Example Spring Boot health groups (indicator IDs omit the `Indicator` suffix):

```properties
management.endpoint.health.group.liveness.include=livenessState,gear4jPersistenceLiveness
management.endpoint.health.group.readiness.include=readinessState,gear4jPersistenceReadiness
```

Do not include the database-backed readiness indicator in the liveness group: a
database incident must remove the instance from traffic, not force a restart
loop.

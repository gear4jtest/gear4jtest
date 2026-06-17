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
  metrics:
    enabled: true
```

Persistence is opt-in. When enabled, `gear4j.persistence.dialect` is mandatory.
Gear4J never auto-detects the database dialect.

`gear4j.persistence.auto-create-tables` defaults to `false`. With the default,
Gear4J expects the core schema to already exist, typically because the host
application applied the SQL resources through Flyway, Liquibase or another
migration process. Set it to `true` only when Gear4J should create and migrate
its own internal schema at startup.


`gear4j.persistence.flush-threads` defaults to `1`, which keeps JDBC persistence
ordering simple and conservative. Higher-throughput applications may increase it
when the database and repository implementation can absorb concurrent batch
flushes.

## Actuator health

If Spring Boot Actuator is on the classpath and JDBC persistence is enabled, the
starter contributes a `gear4jPersistenceHealthIndicator` bean. The indicator is
`UP` when persistence statistics can be read and includes the current number of
active run buffers, buffered station logs, scheduled/completed/failed flushes and
rejected appends. Failed flushes or rejected appends make the indicator `DOWN`
because persistence is no longer healthy. If the manager cannot expose its
snapshot, the indicator is `DOWN` with the thrown exception.

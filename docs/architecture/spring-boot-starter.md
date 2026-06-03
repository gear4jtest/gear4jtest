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
    flush-interval: 1s
    shutdown-timeout: 30s
  metrics:
    enabled: true
```

Persistence is opt-in. When enabled, `gear4j.persistence.dialect` is mandatory.
Gear4J never auto-detects the database dialect.

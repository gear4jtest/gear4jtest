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

`gear4j.persistence.auto-create-tables` defaults to `false`. With the default,
Gear4J expects the core schema to already exist, typically because the host
application applied the SQL resources through Flyway, Liquibase or another
migration process. Set it to `true` only when Gear4J should create and migrate
its own internal schema at startup.

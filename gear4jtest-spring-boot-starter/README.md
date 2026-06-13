# gear4jtest-spring-boot-starter

Spring Boot auto-configuration for Gear4J.

The starter imports the plain Spring integration, validates `gear4j.*` properties,
can wire JDBC persistence when explicitly enabled, and automatically adds
Micrometer runtime metrics when a `MeterRegistry` bean is available.

When Spring Boot Actuator is present and JDBC persistence is enabled, the starter also contributes a `gear4jPersistenceHealthIndicator` bean exposing persistence buffer and flush statistics.

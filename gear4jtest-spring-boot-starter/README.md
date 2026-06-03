# gear4jtest-spring-boot-starter

Spring Boot auto-configuration for Gear4J.

The starter imports the plain Spring integration, validates `gear4j.*` properties,
can wire JDBC persistence when explicitly enabled, and automatically adds
Micrometer runtime metrics when a `MeterRegistry` bean is available.

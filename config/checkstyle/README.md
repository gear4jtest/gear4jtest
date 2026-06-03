# Checkstyle

Gear4J uses Checkstyle as a lightweight contribution guardrail.

Spotless owns formatting. Checkstyle owns project hygiene rules that a formatter should not silently fix, such as
forbidden legacy test imports, redundant imports, `printStackTrace()` and accidental console output in production code.

The current ruleset is intentionally small. Add new rules only when they prevent recurring project-specific mistakes and
can be introduced without creating noisy refactors.

## IntelliJ IDEA

The Gradle build sets `config/checkstyle` as the Checkstyle configuration directory. This exposes `config_loc` to
`checkstyle.xml`, allowing the suppression file to be referenced portably. The optional CheckStyle-IDEA plugin can use
the same directory. This is only an editor convenience; `./gradlew check` is the authoritative validation.

## Runtime compatibility

Checkstyle is pinned to `10.26.1` because Gear4J currently builds on Java 17. Do not upgrade to Checkstyle 13.x unless
the build runtime moves to Java 21.

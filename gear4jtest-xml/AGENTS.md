# gear4jtest-xml agent notes

This module owns XML validation, parsing and Java source generation.

## Do not

- Do not duplicate artifact-store or classloader responsibilities from `gear4jtest-external-api`.
- Do not change generated-code contracts without updating external-api tests and docs.
- Do not generate unreadable Java unless there is a strong reason.
- Do not bypass `GeneratedAssemblyLine` or `@Inject` field injection.

## Generated Java rules

Generated Java should:

- implement `io.test.gear4jtest.external.api.loader.GeneratedAssemblyLine`;
- be no-arg constructible;
- use `@Inject` fields for XML-declared external services;
- prefer imports/static imports where safe;
- preserve enough explicit generic information to compile without unsafe call-site casts;
- stay deterministic for snapshot-like assertions.

## Formatting

Repository-wide formatting instructions from the root `AGENTS.md` apply here. Do not manually mimic the Java style; use
the Gradle formatter and respect Checkstyle failures.

Before finishing code changes in this module, run when possible:

```bash
./gradlew spotlessApply
./gradlew check
```

## Test focus

For generator changes, test the full path whenever possible:

1. XML validation;
2. parsing;
3. Java source generation;
4. formatting;
5. JDT compilation;
6. class instantiation;
7. dependency injection;
8. pipeline execution.

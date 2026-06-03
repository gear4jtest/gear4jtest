# gear4jtest-external-api agent notes

This module owns common infrastructure for external pipeline definitions.

## Do not

- Do not add XML-specific parsing or generation logic here.
- Do not couple this module to Spring.
- Do not move runtime execution behavior out of `gear4jtest-core` into this module.
- Do not document alias invalidation or durable cache behavior as implemented unless the code actually implements it.

## Important contracts

- Translators implement `OperationChainTranslator`.
- Translators return fully-qualified generated class names and formatted Java source.
- Generated classes implement `GeneratedAssemblyLine`.
- Generated classes should remain no-arg constructible.
- Dependencies are injected after instantiation through `DependencyInjector`.
- `AssemblyLineManager` coordinates artifact lookup, translation, compilation, classloading and injection.

## Formatting

Repository-wide formatting instructions from the root `AGENTS.md` apply here. Do not manually mimic the Java style; use
the Gradle formatter and respect Checkstyle failures.

Before finishing code changes in this module, run when possible:

```bash
./gradlew spotlessApply
./gradlew check
```

## Test focus

For external-loading changes, prefer tests that cover:

1. artifact storage;
2. translator resolution;
3. Java generation result contract;
4. in-memory compilation;
5. class loading;
6. dependency injection;
7. generated pipeline execution where practical.

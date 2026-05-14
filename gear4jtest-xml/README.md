# gear4jtest-xml

`gear4jtest-xml` translates XML pipeline definitions into generated Java classes targeting the current Gear4J core API.

The module aligns with `gear4jtest-external-api` and exposes `XmlOperationChainTranslator` through `ServiceLoader`.

## Responsibilities

This module owns:

- XML media type support;
- validation against `assembly-line.xsd`;
- parsing XML into an internal model;
- generating readable Java source;
- formatting generated Java source;
- registering the XML translator as an `OperationChainTranslator`.

It should not own artifact storage, publication workflow, classloader caching or runtime execution. Those concerns
belong to `gear4jtest-external-api` and `gear4jtest-core`.

## Supported media types

- `application/xml`
- `text/xml`
- `application/vnd.gear4j.pipeline+xml`
- vendor media types ending with `+xml`

## Generated class contract

The generated Java source must:

- be fully-qualified;
- implement `io.test.gear4jtest.external.api.loader.GeneratedAssemblyLine`;
- expose a no-arg constructor;
- build an `AssemblyLine` using the current core Java API;
- generate dependency fields annotated with `@Inject` when XML references external services;
- avoid constructor injection so `AssemblyLineManager` can instantiate first and inject later.

## Generation pipeline

Typical path:

1. `XmlOperationChainTranslator` receives XML bytes and media type.
2. `AssemblyLineValidator` validates the XML.
3. `XmlPipelineParser` parses XML into `XmlPipelineDefinition`.
4. `XmlToJavaGenerator` generates Java source.
5. `JdtFormatter` formats the generated source.
6. `AssemblyLineManager` compiles and loads the generated class through the external API module.

## Branch ids

Container `subLine` elements and `ifElseContainer` conditional operations must define explicit branch ids. Branch ids
are functional keys used by sibling outcomes and generated Java must preserve them deterministically.

## Current generation direction

Generated Java should be readable enough for debugging.

Prefer:

- clear method names;
- imports instead of unreadable fully-qualified types when safe;
- static imports for builder helpers when they improve readability;
- explicit generic types where generated code otherwise fails compilation;
- deterministic output so tests remain stable.

## ServiceLoader registration

The module registers:

```text
META-INF/services/io.test.gear4jtest.external.api.translator.OperationChainTranslator
```

This allows `OperationChainTranslatorResolver.fromServiceLoader(...)` to discover the XML translator.

## Samples

Sample XML files live under:

```text
src/test/resources/samples/
src/main/resources/sample-assembly-line.xml
```

The XSD lives at:

```text
src/main/resources/assembly-line.xsd
```

## Testing

Useful focused tasks:

```bash
./gradlew :gear4jtest-xml:test
./gradlew :gear4jtest-xml:test --tests '*XmlOperationChainTranslatorTest'
```

For meaningful XML changes, tests should cover the full path: validate, parse, generate, compile, instantiate, inject
and execute.

## Code style

Repository formatting is enforced by Spotless from the root Gradle build. Use `./gradlew spotlessApply` before
committing code changes and `./gradlew check` for full validation.

Generated Java source is formatted by the XML generator for readability. Generated files under `build/` are excluded
from repository style validation.

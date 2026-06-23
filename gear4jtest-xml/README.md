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

The module also contains the first minimal Gear Expression Language (GEL) parser/evaluator under
`io.github.gear4jtest.xml.expression`. GEL is the safe alternative to inline Java for untrusted or BO-authored conditions. Trusted Java snippets remain available only through explicit trusted generator/translator factories.

## Supported media types

- `application/xml`
- `text/xml`
- `application/vnd.gear4j.pipeline+xml`
- vendor media types ending with `+xml`

## Generated class contract

The generated Java source must:

- be fully-qualified;
- implement `io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine`;
- expose a no-arg constructor;
- build an `AssemblyLine` using the current core Java API;
- generate dependency fields annotated with `@Inject` when XML references external services;
- avoid constructor injection so `AssemblyLineManager` can instantiate first and inject later.

## Generation pipeline

Typical path:

1. `XmlOperationChainTranslator` receives XML bytes and media type.
2. `AssemblyLineValidator` validates the XML.
3. `XmlAssemblyLineParser` parses XML into `XmlAssemblyLineDefinition`.
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



## Containers and signals

Generated containers target the current typed container API. XML `subLine` ids are emitted as deterministic branch ids and `returnsFunction` lambdas receive `ContainerResults`:

```xml
<container id="parallelContainer"
           inputType="java.lang.String"
           outputType="java.util.List&lt;java.lang.String&gt;"
           parallel="true">
  <subLines>
    <subLine id="alpha">...</subLine>
    <subLine id="beta">...</subLine>
    <subLine id="gamma">...</subLine>
  </subLines>
  <returnsFunction expression="results -> results.orderedOutputs()"/>
</container>
```

`orderedOutputs()` is intended for XML/dynamic aggregation where branch handles are not available in handwritten code. Java-authored pipelines should prefer `Stations.branch(...)` handles and `results.get(branch)`.

Flow signal stations and error policies intentionally use different domains:

- `<signal type="STOP|FATAL">` is a flow-control station and cannot use `IGNORE`;
- `<safeError>` / `<unsafeError>` `signalType` may use `STOP`, `FATAL` or `IGNORE` because it describes an error-handling policy.

## Generated source formatting

Generated Java is formatted through the `JavaSourceFormatter` abstraction. The default `XmlToJavaGenerator` uses a
small built-in Eclipse JDT profile aimed at readable generated code. Applications embedding the generator may provide
another formatter, including one created from an Eclipse formatter XML profile:

```java
var generator = XmlToJavaGenerator.trusted(
        "com.myorg.generated",
        classLoader,
        JdtFormatter.fromEclipseProfile(Path.of("config/formatter/eclipse-java-formatter.xml"), "MyProject"));
```

The default `XmlToJavaGenerator.builder(...).build()` / `XmlToJavaGenerator.untrusted()` policy rejects inline Java snippets.
Use `language="gel"` on `<condition>` elements for untrusted XML conditions, and use `XmlToJavaGenerator.trusted(...)` only for reviewed XML definitions that are
allowed to generate Java source. This keeps user-generated classes aligned with
the consuming project when desired, without coupling them to Gear4J's own
repository formatter.

## ServiceLoader registration

The module registers:

```text
META-INF/services/io.github.gear4jtest.external.api.translator.OperationChainTranslator
```

This allows `OperationChainTranslatorResolver.fromServiceLoader(...)` to discover the XML translator. The discovered
translator uses the restrictive GEL-only/no-inline-Java policy by default. For reviewed build-time or trusted runtime generation,
inject `XmlOperationChainTranslator.trusted()` explicitly.

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

# gear4jtest-xml

`gear4jtest-xml` translates XML pipeline definitions into generated Java classes targeting the current Gear4J core API.

The module aligns with `gear4jtest-external-api` and exposes `XmlOperationChainTranslator` through `ServiceLoader`.

## Responsibilities

This module owns:

- XML media type support;
- validation against `assembly-line.xsd`;
- parsing XML into an internal model;
- semantic validation of generated identifiers, collisions and Java type names;
- generating readable Java source;
- formatting generated Java source;
- registering the XML translator as an `OperationChainTranslator`.

It should not own artifact storage, publication workflow, classloader caching or runtime execution. Those concerns
belong to `gear4jtest-external-api` and `gear4jtest-core`.

The module also contains the first minimal Gear Expression Language (GEL) parser/evaluator under
`io.github.gear4jtest.xml.expression`. GEL is the restricted alternative to
inline Java for untrusted or BO-authored conditions. A separate
`XmlOperatorCapabilityPolicy` allowlists the executable operators available to
each definition in TEST and RUN. Trusted Java snippets and operator class names
remain available only through explicit trusted generator/translator factories.

GEL property access is deny-by-default for Java objects. Maps are copied to an
inert context representation. Direct evaluator users can approve exact runtime
types and properties, then optionally snapshot an approved record before
evaluation:

```java
var policy = PropertyAccessPolicy.allowlist()
        .allowRecordType(OrderView.class)
        .build();
var inertInput = GearExpressionValues.snapshot(orderView, policy);
boolean accepted = GearExpressionParser.parse("input.status == 'READY'")
        .evaluateBoolean(GearExpressionContext.ofInput(inertInput));
```

Existing applications can temporarily call `GearExpressionContext.legacy(...)`.
That mode logs newly invoked accessors, is deprecated for removal and is unsafe
for untrusted object graphs. Generated XML always uses secure defaults, so rich
objects must be converted upstream to inert maps before a GEL condition reads
their properties.

## Supported media types

- `application/xml`
- `text/xml`
- `application/vnd.gear4j.assembly-line+xml` (canonical)
- vendor media types ending with `+xml`

## Generated class contract

The generated Java source must:

- be fully-qualified;
- implement `io.github.gear4jtest.external.api.loader.GeneratedAssemblyLine<IN, OUT>`;
- expose a no-arg constructor;
- build an `AssemblyLine` using the current core Java API;
- generate dependency fields annotated with `@Inject` when XML references external services;
- avoid constructor injection so `AssemblyLineManager` can instantiate first and inject later.

## Generation pipeline

Typical path:

1. `XmlOperationChainTranslator` receives XML bytes and media type.
2. `AssemblyLineValidator` validates the XML.
3. `XmlAssemblyLineParser` parses XML into `XmlAssemblyLineDefinition`.
4. restricted generation resolves every operator capability for the requested
   `ExecutionMode`;
5. semantic validation rejects invalid generated Java identifiers, normalized
   name collisions, duplicate branch ids and malformed type names with the
   affected XML path;
6. structural limits reject excessive operation count, dependency count or
   nesting depth;
7. `XmlToJavaGenerator` generates Java source and checks its raw and formatted
   UTF-8 size.
8. `JdtFormatter` formats the generated source.
9. `AssemblyLineManager` compiles and loads the generated class through the external API module.

## Semantic validation

The XSD validates document structure, but Java names and type expressions need
additional checks after parsing. Before rendering starts, the generator:

- validates every generated class, method, dependency and parallel-executor
  field as a Java 17 identifier;
- rejects Java keywords such as a dependency named `class`;
- rejects collisions after deterministic normalization, such as `foo-bar` and
  `foo_bar`;
- rejects duplicate branch ids among siblings;
- parses every declared Java type completely instead of accepting a valid
  prefix followed by trailing text.

Failures use `XmlDefinitionValidationException`. Its `path()` and
`rejectedValue()` accessors let a BO or API return the offending definition
location without waiting for a Java compiler diagnostic.

## Translation budgets

Every translator applies finite defaults: 1,000 total operations, 256
dependencies, nesting depth 32 and 4 MiB of generated UTF-8 Java source. Nested
iterator, container and if/else operations all contribute to the same operation
budget. Limits apply to trusted XML as well as restricted XML.

Applications may lower or deliberately raise finite limits:

```java
XmlTranslationLimits limits = XmlTranslationLimits.defaults()
        .withMaxOperations(250)
        .withMaxDependencies(32)
        .withMaxNestingDepth(12)
        .withMaxGeneratedSourceBytes(1024L * 1024L);

XmlOperationChainTranslator translator =
        XmlOperationChainTranslator.gelOnly(operatorCapabilities, limits);
```

The runtime compiler independently enforces its source and bytecode limits, so a
custom translator cannot bypass the external loading boundary.

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

The default `XmlToJavaGenerator.builder(...).build()` /
`XmlToJavaGenerator.untrusted()` policy rejects inline Java and all operator
references. Use `language="gel"` plus a configured
`XmlOperatorCapabilityPolicy` for restricted XML. Use
`XmlToJavaGenerator.trusted(...)` only for reviewed XML definitions that may
name classes and generate Java source.

## ServiceLoader registration

The module registers:

```text
META-INF/services/io.github.gear4jtest.external.api.translator.OperationChainTranslator
```

This allows `OperationChainTranslatorResolver.fromServiceLoader(...)` to
discover the XML translator. The discovered translator is GEL-only with an
empty operator capability allowlist. Inject
`XmlOperationChainTranslator.gelOnly(policy)` for restricted definitions or
`XmlOperationChainTranslator.trusted()` for reviewed source.

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

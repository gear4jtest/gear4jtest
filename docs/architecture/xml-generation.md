# XML generation architecture

## Status

Implemented, evolving.

## Intent

`gear4jtest-xml` turns XML pipeline definitions into Java source code that targets the core Gear4J API.

The generated source should be both executable and readable enough to debug when a translation test fails.

## Main components

| Component                     | Role                                                        |
|-------------------------------|-------------------------------------------------------------|
| `AssemblyLineValidator`       | Validates XML against the schema.                           |
| `XmlAssemblyLineParser`       | Parses XML into an internal model.                          |
| `XmlAssemblyLineDefinition`   | Internal model used by the generator.                       |
| `XmlOperatorCapabilityPolicy` | Resolves restricted operator ids independently per mode.    |
| `XmlDefinitionSemanticValidator` | Rejects invalid names, collisions and malformed types.   |
| `XmlToJavaGenerator`          | Generates Java source.                                      |
| `JdtFormatter`                | Formats generated Java.                                     |
| `XmlOperationChainTranslator` | Exposes XML translation through the external-api SPI.       |

## Generation rules

Generated code should:

- compile against the current `gear4jtest-core` API;
- implement `GeneratedAssemblyLine`;
- avoid constructor injection;
- expose XML dependencies through `@Inject` fields;
- use clear generated method names;
- preserve generic type information where needed;
- use imports and static imports when they improve readability safely;
- remain deterministic across runs.

## Branch id generation

XML branch ids must be carried into generated Java builder calls. Generated code should never invent random branch ids or
fall back silently to station ids for container branches.


## Container generation

XML containers are generated against the single post-H.2 container API:

```java
Stations.container("enrich-input", Input.class)
        .withBranch("alpha", alpha())
        .withBranch("beta", beta())
        .returns(results -> results.orderedOutputs());
```

Generated XML code may use `results.orderedOutputs()` because XML definitions do not expose Java branch-handle variables to authors. Handwritten Java should prefer typed `ContainerBranch<IN, OUT>` handles and `results.get(branch)`.

Do not generate or reintroduce arity-specific container APIs, positional varargs aggregators, `withSubLine(...)`, or legacy umbrella builder helpers.

## Signal generation

Flow signal stations and error policies are separate in XML and generated Java:

- `<signal type="STOP|FATAL">` generates `SignalStation` with `SignalType.STOP` or `SignalType.FATAL`;
- `IGNORE` is invalid for `<signal>`;
- error handlers may still use `signalType="IGNORE"` because that belongs to error-policy semantics.

## Validation rules

Validation should happen before Java generation.

Generator code should not rely on malformed XML being impossible unless the validator or parser enforces it.
Restricted generation must recursively resolve every processing operation
through `XmlOperatorCapabilityPolicy` before the first Java source fragment is
rendered. A nested iterator, container or if/else operation cannot bypass the
same policy applied to top-level operations.

After capability resolution and before rendering, semantic validation applies
to both trusted and restricted definitions. It validates Java 17 identifiers
after the same deterministic normalization used by the renderer, keeps one
namespace for generated dependency and executor fields, rejects sibling branch
id duplicates, and requires complete parsing of every declared Java type.
`XmlDefinitionValidationException` reports the XML path and rejected value so
definition authors do not receive a late compiler-only diagnostic.

## Testing strategy

Simple generator unit tests are useful, but the most important tests are end-to-end:

1. parse sample XML;
2. resolve operator capabilities for TEST and RUN;
3. validate semantic names, collisions and types;
4. generate Java;
5. compile the generated source;
6. instantiate the generated class;
7. inject dependencies;
8. execute the resulting pipeline.

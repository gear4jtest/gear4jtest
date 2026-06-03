# XML generation architecture

## Status

Implemented, evolving.

## Intent

`gear4jtest-xml` turns XML pipeline definitions into Java source code that targets the core Gear4J API.

The generated source should be both executable and readable enough to debug when a translation test fails.

## Main components

| Component                     | Role                                                  |
|-------------------------------|-------------------------------------------------------|
| `AssemblyLineValidator`       | Validates XML against the schema.                     |
| `XmlPipelineParser`           | Parses XML into an internal model.                    |
| `XmlPipelineDefinition`       | Internal model used by the generator.                 |
| `XmlToJavaGenerator`          | Generates Java source.                                |
| `JdtFormatter`                | Formats generated Java.                               |
| `XmlOperationChainTranslator` | Exposes XML translation through the external-api SPI. |

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

## Validation rules

Validation should happen before Java generation.

Generator code should not rely on malformed XML being impossible unless the validator or parser enforces it.

## Testing strategy

Simple generator unit tests are useful, but the most important tests are end-to-end:

1. parse sample XML;
2. generate Java;
3. compile the generated source;
4. instantiate the generated class;
5. inject dependencies;
6. execute the resulting pipeline.

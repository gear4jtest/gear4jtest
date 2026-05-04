# gear4jtest-xml

XML translator plugin for externalized Gear4J pipelines.

The module now aligns with `gear4jtest-external-api`:

- it exposes `XmlOperationChainTranslator` through `ServiceLoader`;
- XML definitions are validated against `assembly-line.xsd`;
- XML is parsed into a small internal model;
- the model is translated to Java source implementing `io.test.gear4jtest.external.api.loader.GeneratedAssemblyLine`;
- the generated Java targets the current `gear4jtest-core` API.

Supported media types:

- `application/xml`
- `text/xml`
- `application/vnd.gear4j.pipeline+xml`
- any `+xml` vendor media type

The generated source intentionally uses no constructor injection. XML dependencies are generated as fields annotated with
`@Inject`, so `AssemblyLineManager` can instantiate the class with a no-arg constructor and then delegate dependency
resolution to its `DependencyInjector`.

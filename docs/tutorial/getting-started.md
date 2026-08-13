# Build and run a first Gear4J pipeline

This tutorial builds a two-station Java pipeline, supplies its operators, executes one typed request and inspects the
terminal outcome. It uses Java 17 and only the public API/SPI surface intended for Gear4J 1.x applications.

The complete source is the
[compile-backed getting-started example](../../config/consumer-smoke/src/main/java/io/github/gear4jtest/consumer/GettingStartedExample.java).
The release gate compiles and runs that source against staged Maven artifacts, so it cannot silently drift from the
published API.

## 1. Add the core module

Replace `<gear4j-version>` with the Gear4J version used by the application.

```groovy
dependencies {
    implementation 'io.github.gear4jtest:gear4jtest-core:<gear4j-version>'
}
```

The equivalent Maven dependency is:

```xml
<dependency>
  <groupId>io.github.gear4jtest</groupId>
  <artifactId>gear4jtest-core</artifactId>
  <version>&lt;gear4j-version&gt;</version>
</dependency>
```

Gear4J 1.x requires Java 17. Published dependencies resolve from Maven Central; no Gear4J-specific repository is
required for a stable release.

## 2. Write typed operators

An `Operator<IN, OUT>` receives the previous station output and a `StationExecutionContext`. The first operator trims
the request value. The second reads a typed value from the run context and formats the result.

```java
public static final class NormalizeName implements Operator<String, String> {
    @Override
    public String transform(String input, StationExecutionContext context) {
        return input.trim();
    }
}

public static final class FormatGreeting implements Operator<String, String> {
    @Override
    public String transform(String input, StationExecutionContext context) {
        String salutation = context.getGlobalContext()
                .find("salutation", String.class)
                .orElse("Hello");
        return salutation + ", " + input + "!";
    }
}
```

Keep business inputs in the typed value flowing between stations. Use the run context for cross-cutting values that are
shared deliberately across a run. Run-scoped capabilities such as resource lookup and event publication are available
through `context.getServices()`.

## 3. Supply operator instances

Gear4J asks a `ResourceFactory` for each operator class. The tutorial uses a minimal reflective implementation:

```java
private static final class ReflectiveResourceFactory implements ResourceFactory {
    @Override
    public <T> T getResource(Class<T> type) {
        try {
            return type.cast(type.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create " + type.getName(), exception);
        }
    }
}
```

This is sufficient for operators with an accessible no-argument constructor. Applications that need dependency
injection should provide their container-backed factory. The `gear4jtest-spring` module already provides
`SpringResourceFactory` and a configured `AssemblyLineExecutor` bean.

## 4. Compose the assembly line

Each `then(...)` call advances the builder output type. The next station must accept that type, so incompatible chains
fail during Java compilation.

```java
AssemblyLine<String, String> greetingLine = AssemblyLines.<String>createAssemblyLine("greeting")
        .then(processingOperation("normalize-name", NormalizeName.class).build())
        .then(processingOperation("format-greeting", FormatGreeting.class).build())
        .build();
```

Pipeline and station identifiers are operational identities used by traces, metrics policies and persistence. Keep
them stable and do not derive them from request data.

## 5. Execute a typed request

Create the public executor facade once and normally reuse it. `RunRequest` contains values that vary for one execution;
pipeline defaults belong on the `AssemblyLine` builder.

```java
ResourceFactory resources = new ReflectiveResourceFactory();
AssemblyLineExecutor executor = AssemblyLineExecutors.create(resources);

RunRequest<String> request = RunRequest.builder()
        .input(" Ada ")
        .context(Map.of("salutation", "Hello"))
        .build();

ExecutionResult<String> result = executor.execute(greetingLine, request);
```

Inspect the terminal outcome before consuming the value:

```java
if (!result.isSuccess()) {
    throw new IllegalStateException("Pipeline ended with " + result.getOutcome(), result.getError());
}

String greeting = result.getResult(); // "Hello, Ada!"
RunTrace trace = result.getExecution();
```

`ExecutionResult` distinguishes success, skip, stop, cancellation and normalized failure. Do not use a null result as a
proxy for terminal state. `RunTrace` and its station views are read-only; engine and mutable trace implementations are
not application contracts.

## 6. Choose the next integration

- Add `gear4jtest-jackson` when mutable branch inputs need Jackson-backed deep cloning.
- Add `gear4jtest-spring` for plain Spring resource resolution and executor wiring.
- Add `gear4jtest-spring-boot-starter` for Boot properties, optional JDBC persistence, health and Micrometer wiring.
- Add `gear4jtest-xml` and the `io.github.gear4jtest.xml2java` plugin for reviewed generated definitions.
- Read the [1.0 migration guide](../migration/to-1.0.md) before moving code that imports engine, execution or mutable
  trace implementation packages.

The [core module guide](../../gear4jtest-core/README.md) covers station types, failure flow, events, context propagation,
worker concurrency, nested assembly-line calls and persistence contracts after this first pipeline.

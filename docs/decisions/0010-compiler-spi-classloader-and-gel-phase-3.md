# 0010 — Compiler SPI, GEL-only XML and generated classloader lifecycle

## Status

Accepted.

## Context

Gear4J can translate external XML definitions into generated Java source, compile
that source and load it into the running JVM. This is powerful, but it creates
three long-term operational risks:

1. hard coupling to Eclipse JDT internals;
2. unsafe external XML if Java snippets are accepted from non-trusted authors;
3. unbounded classloader growth when aliases such as `latest` churn over time.

## Decision

### Compiler SPI

`GeneratedSourceCompiler` is the stable compiler SPI. The built-in default now
prefers `JavaxToolsGeneratedSourceCompiler`, based on the standard JDK
`javax.tools.JavaCompiler`, and falls back to JDT when the runtime image does not
provide `jdk.compiler` or javac cannot compile with the current runtime classpath.
Applications can still force JDT, force javac, or provide an alternative through
constructor injection or `GeneratedSourceCompilers.fromServiceLoader(...)`.

### XML security boundary

The default XML translator/generator remains untrusted. It rejects inline Java
snippets and allows only GEL conditions when expressions are needed in untrusted
XML. Trusted build-time generation must opt in explicitly with
`XmlOperationChainTranslator.trusted()`.

### Classloader lifecycle

`InMemoryClassLoaderRegistry` is now bounded. It uses a best-effort LRU policy
and protects aliased loaders from automatic eviction so aliases never point to a
missing classloader. Operators that expect high version churn should configure a
capacity that matches their promotion/rollback window. A separate
`maxProtectedLoaders` cap prevents alias-heavy deployments from protecting an
unbounded number of classloaders from eviction.

## Consequences

- The standard JDK compiler is the preferred path when available; JDT remains the fallback.
- Untrusted XML has a concrete GEL-only path for conditions.
- Runtime classloader churn is limited by default, while exact aliases remain
  safe up to an explicit protected-loader cap.

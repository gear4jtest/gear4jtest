# 0010 — Compiler SPI, GEL-only XML and generated classloader lifecycle

## Status

Accepted. The XML decision is complemented by ADR 0033, which closes the
operator-selection capability boundary.

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
`javax.tools.JavaCompiler`, and selects JDT only when the runtime image does not
provide `jdk.compiler`. Backend selection happens once; a source error from javac
is not compiled a second time with JDT. The javac implementation augments its
classpath from file-based parent-classloader URLs, while JDT is configured with
Java 17 release semantics. Applications can still force JDT, force javac, or
provide an alternative through constructor injection or
`GeneratedSourceCompilers.fromServiceLoader(...)`.

### XML security boundary

The default XML translator/generator remains untrusted. It rejects inline Java
snippets and allows only GEL conditions when expressions are needed in untrusted
XML. Trusted build-time generation must opt in explicitly with
`XmlOperationChainTranslator.trusted()`. ADR 0033 additionally makes the
restricted operator allowlist deny-by-default and mode-aware.

### Classloader lifecycle

`InMemoryClassLoaderRegistry` is now bounded. It uses a best-effort LRU policy
and protects aliased loaders from automatic eviction so aliases never point to a
missing classloader. Operators that expect high version churn should configure a
capacity that matches their promotion/rollback window. A separate
`maxProtectedLoaders` cap prevents alias-heavy deployments from protecting an
unbounded number of classloaders from eviction.

## Consequences

- The standard JDK compiler is the preferred path when available; JDT is selected only when javac is unavailable.
- JDT internal imports stay confined to the `@Internal` adapter. Upgrading the pinned JDT version requires the focused
  Java 17 compilation, class loading, class-file target and diagnostic compatibility test to pass.
- Syntax or type errors are reported once by the selected backend rather than compiled twice.
- Untrusted XML has a concrete GEL-only path for conditions.
- Runtime classloader churn is limited by default, while exact aliases remain
  safe up to an explicit protected-loader cap.

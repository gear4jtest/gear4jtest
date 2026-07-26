# 0031 — Generated-code budgets are end-to-end hard limits

## Status

Accepted.

## Context

The completed-compilation cache had a 16 MiB bytecode budget, but that value was
not an admission limit. A larger compiler result bypassed the cache and was
still copied, returned, retained by an `InMemoryClassLoader` and registered.
The classloader registry bounded entry count but not cumulative weight, and XML
bounded input bytes without bounding structural complexity or generated source.

This left heap and metaspace exposure proportional to attacker- or
operator-controlled generated code.

## Decision

- XML translation defaults to 1,000 total operations, 256 dependencies,
  operation nesting depth 32 and 4 MiB of raw or formatted UTF-8 Java source.
- The parser consumes operation/dependency/depth budgets while building the
  model. The standalone generator validates manually constructed models again.
- Generated compilation rejects source above 4 MiB before executor dispatch and
  compiler output above 8 MiB before copying, caching or classloading.
- The existing 16 MiB completed-cache budget remains a separate eviction
  concern.
- The default classloader registry limits cumulative generated-bytecode weight
  to 64 MiB in addition to its 256-loader count.
- Defined class bytes are released from the loader's heap map. Their original
  size remains charged to the registry because defined classes still consume
  metaspace.
- A loader registration fails if aliases protect every eviction candidate needed
  to satisfy the hard bytecode budget.

## Consequences

Oversized publication validation and runtime loading fail consistently with no
uncached bypass. XML that was previously accepted can now fail when it exceeds a
finite default; this is an intentional pre-1.0 availability hardening.

Applications can roll back an unexpectedly strict threshold by raising the
corresponding finite `XmlTranslationLimits`,
`GeneratedCompilationConfiguration` or
`InMemoryClassLoaderRegistry.Builder` value. Disabling hard source, output or
classloader-weight admission entirely is intentionally unsupported.

The compiler still runs in-process and interruption remains cooperative. Truly
hostile definitions require process or container isolation with OS-level heap,
CPU, deadline and I/O limits.

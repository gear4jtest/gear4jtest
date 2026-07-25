# 0030 — Generated compilation and Gradle XML generation are cache-aware

## Status

Accepted.

## Context

Generated Java source was compiled during publication validation and then often
compiled again during the first runtime load. The default compiler also retried
all javac failures with JDT, making invalid sources twice as expensive and
producing environment-dependent diagnostics.

The XML Gradle task was annotated `@CacheableTask`, but its action accessed
`Project` through `project.delete(...)`. Existing tests used `ProjectBuilder`, and
CI only exercised `help` with the configuration cache, so the real task contract
was not proven.

## Decision

- Select javac or JDT once when the default compiler is created.
- Use JDT only when `jdk.compiler` is unavailable, never as a retry for a javac
  source error.
- Resolve file-based entries visible from the supplied parent classloader when
  building the javac classpath.
- Configure JDT with Java 17 source, target, compliance and release semantics.
- Share a bounded 128-entry/16-MiB single-flight compilation cache between publication
  validation and runtime loading. Failed compilations are not cached.
- Execute compiler delegates in an owned bounded executor with a finite
  end-to-end deadline. The default policy is one worker, 32 queued distinct
  compilations and a 30-second timeout.
- Publish timeout, duration, cache and saturation counters through
  `AssemblyLineManager.compilationStats()`.
- Inject `FileSystemOperations` into `XmlAssemblyLineGenerateTask` and avoid
  accessing `Project` from the task action.
- Validate the real task with Gradle TestKit, strict configuration-cache checks
  and build-cache restoration.

## Consequences

- Invalid source is compiled once and produces diagnostics from one backend.
- Parent-only dependencies exposed through file-based classloader URLs are
  visible to javac.
- Validation and first runtime loading normally reuse the same bytecode.
- Compilation results are bounded and defensively copied; custom compilers must
  remain deterministic for identical input.
- A timeout wakes every waiter for the same source and permits a later retry.
  Cancellation remains best-effort for compiler implementations that ignore
  interruption; late bytecode is discarded.
- `AssemblyLineManager` owns the compilation workers and is `AutoCloseable`.
- The XML generation task can be restored from the build cache and its
  configuration can be reused safely.

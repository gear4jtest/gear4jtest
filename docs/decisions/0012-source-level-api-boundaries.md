# Source-level API boundaries before JPMS

## Status

Accepted.

## Context

Gear4J exposes source marker annotations (`@PublicApi`, `@Spi`, `@Internal`,
`@Experimental`) but the project does not currently publish Java Platform Module
System descriptors. Until JPMS is introduced, Java visibility alone cannot stop
applications from depending on implementation packages such as the engine,
runner chain or mutable execution traces.

Adding JPMS now would be a larger compatibility and build-management task than
the current MVP needs. The immediate risk is not lack of module descriptors; it
is unclear compatibility expectations and accidental growth of API/SPI
references to implementation packages.

## Decision

Gear4J keeps source/class-file marker annotations as the compatibility contract
for now and adds source-level architecture tests instead of introducing JPMS in
this phase.

The rules are:

- every production package must declare a package-level stability marker in
  `package-info.java`;
- packages marked `@PublicApi` or `@Spi` are compatibility-sensitive;
- packages marked `@Internal` may change without compatibility guarantees;
- packages marked `@Experimental` are intentionally unstable until promoted;
- public packages may temporarily contain individual implementation classes
  marked `@Internal`, but consumers must not treat those types as stable;
- existing API/SPI references to implementation packages are captured as a
  baseline debt list and must not grow without an explicit design decision.

## Consequences

This does not provide compile-time encapsulation for downstream consumers. It
makes the boundary visible in Javadocs and keeps the repository honest while the
API is still evolving.

A future pre-1.0 stabilization pass can replace or complement these checks with
JPMS descriptors once the module graph and advanced extension points are stable.

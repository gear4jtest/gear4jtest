# 0018 — GEL property access is explicit

## Status

Accepted.

## Context

GEL rejects Java method-call syntax, but its first evaluator still translated a
property path to any matching public record component or JavaBean getter. A
definition author could therefore trigger application code with side effects by
writing a path. Filtering accessor names was not a security boundary.

## Decision

- `GearExpressionContext` uses `PropertyAccessPolicy.secureDefaults()` unless a
  caller supplies another policy.
- Secure defaults evaluate maps from an inert context snapshot and reject Java
  object properties.
- Trusted applications can create an exact-runtime-type allowlist with
  `PropertyAccessPolicy.allowlist()`.
- Allowlisted accessors are resolved once per runtime class through `ClassValue`
  and invoked with cached `MethodHandle` instances.
- `GearExpressionValues.snapshot(...)` converts maps, collections, arrays and
  explicitly approved records to an immutable value tree with depth, node-count
  and cycle guards.
- `legacyBeanAccess()` remains as a deprecated migration mechanism. It logs a
  warning once per accessor and must not be used with untrusted object graphs.
- Java runtime metadata objects and metadata property names remain forbidden,
  including in compatibility mode.
- Equality is limited to inert scalar values so an expression cannot indirectly
  invoke an application object's `equals` implementation.

Allowlist entries match exact runtime classes. An approved base class does not
implicitly approve its subclasses.

## Consequences

This is an intentional pre-1.0 security hardening. Existing GEL expressions
that navigate records or JavaBeans now fail under the default context. Users
must choose one of these migrations:

1. pass maps or precomputed inert snapshots to untrusted GEL;
2. supply an exact allowlist when the evaluated types and accessors are trusted;
3. use the deprecated legacy policy temporarily while inventorying expressions.

Generated XML uses the secure default. A GEL condition over a rich Java object
therefore requires an upstream conversion to inert map data; trusted XML can
still use reviewed inline Java explicitly.

The snapshot boundary prevents an expression from calling an application map
implementation during evaluation. Creating the snapshot necessarily reads the
source values once and must be done by trusted application code.

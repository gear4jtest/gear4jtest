# 0008 — Gear4J Expression Language is the security boundary for untrusted definitions

## Status

Accepted. A minimal safe parser/evaluator MVP exists in `io.github.gear4jtest.xml.expression`. XML integration remains
future work.

## Context

Gear4J XML generation currently supports trusted Java expressions for conditions,
transformers and other generated source fragments. This is powerful for
source-controlled developer-authored XML, but it is not safe for arbitrary user or
BO-authored definitions.

Java source filtering is not a reliable sandbox. With Java 17 there is no general
SecurityManager-based isolation model that can safely execute untrusted code in
the same JVM.

## Decision

Gear4J will keep inline Java as a trusted developer feature, but untrusted or
semi-trusted pipeline definitions must use a restricted Gear4J Expression Language (GEL) once XML integration is
available.

GEL must be designed around an allowlisted AST and evaluator rather than around
string filtering. It must not expose arbitrary Java objects, reflection, class
loading, I/O, networking, process execution or unchecked method invocation.

## Consequences

- Inline Java XML remains a trusted-source feature.
- Future BO editing should emit GEL expressions or a typed expression model, not
  Java snippets.
- The first GEL MVP supports literals, data paths and boolean/equality operators only. It deliberately rejects Java
  method invocation, type lookup, object creation and static access.
- XML validation should eventually be able to reject inline Java unless the
  import/generation mode is explicitly trusted.
- The expression language roadmap remains tracked in
  `docs/roadmap/gear-expression-language.md`.

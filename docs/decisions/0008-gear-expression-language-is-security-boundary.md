# 0008 — Gear4J Expression Language is the security boundary for untrusted definitions

## Status

Accepted. A restricted parser/evaluator and GEL XML conditions are implemented.
ADR 0018 defines the deny-by-default property-access policy added after this
initial language decision. ADR 0033 complements GEL with a mode-aware operator
capability boundary; GEL alone does not control which application code a
definition may invoke.

## Context

Gear4J XML generation currently supports trusted Java expressions for conditions,
transformers and other generated source fragments. This is powerful for
source-controlled developer-authored XML, but it is not safe for arbitrary user or
BO-authored definitions.

Java source filtering is not a reliable sandbox. With Java 17 there is no general
SecurityManager-based isolation model that can safely execute untrusted code in
the same JVM.

## Decision

Gear4J keeps inline Java as a trusted developer feature, but untrusted or
semi-trusted pipeline expressions must use the restricted Gear4J Expression
Language (GEL). Executable operator selection is governed separately by ADR
0033.

GEL must be designed around an allowlisted AST and evaluator rather than around
string filtering. It must not expose arbitrary Java objects, reflection, class
loading, I/O, networking, process execution or unchecked method invocation.

## Consequences

- Inline Java XML remains a trusted-source feature.
- BO editing should emit GEL expressions or a typed expression model, not
  Java snippets.
- The first GEL MVP supports literals, data paths and boolean/equality operators only. It deliberately rejects Java
  method invocation, type lookup, object creation and static access.
- The default XML translator/generator rejects inline Java unless generation is
  explicitly trusted.
- Restricted definitions also require an exact operator capability allowlist;
  see ADR 0033.
- The expression language roadmap remains tracked in
  `docs/roadmap/gear-expression-language.md`.

# Gear4J expression language

## Status

MVP parser/evaluator implemented in `io.github.gear4jtest.xml.expression`; XML integration remains future work. The
security-boundary decision is captured in `docs/decisions/0008-gear-expression-language-is-security-boundary.md`.

This note captures the target direction for replacing inline Java expressions in XML or BO-authored pipeline definitions.

## Problem

Inline Java expressions are powerful for trusted developer-authored XML, but they
are not appropriate for untrusted XML or for a future BO where users can edit
conditions, guards, parameter mappings or fallback expressions.

A safe configuration language must not allow arbitrary Java execution.

## Goal

Introduce a small Gear4J expression language for common pipeline decisions:

```text
input.amount > 100
context.country == "FR"
param("threshold") <= input.score
station("riskScore").status == "SUCCEEDED"
hasCapability("customer-segment")
```

The language should cover practical business needs without exposing arbitrary
Java objects, reflection, I/O, networking or process execution.

## Non-goals

- It is not a general-purpose programming language.
- It does not support loops.
- It does not support object construction.
- It does not support arbitrary method calls.
- It does not expose `Class`, reflection, files, network or system APIs.
- It does not replace Java operations/processors for real business logic.

## Possible model

Expressions should be parsed into an internal AST:

```java
sealed interface GearExpression permits BinaryExpression, LiteralExpression,
        PathExpression, FunctionCallExpression {
}
```

Implemented MVP nodes currently include:

- literals: string, number, boolean, null;
- path access: `input.foo`, `variables.bar` / `context.bar`;
- equality comparisons: `==`, `!=`;
- boolean operators: `&&`, `||`, `!`;
- parentheses.

Future nodes can include:

- ordered comparisons: `<`, `<=`, `>`, `>=`;
- limited functions: `isNull`, `isBlank`, `contains`, `matches`, `hasCapability`;
- station references for BO/debug use, if trace data is available.

## Security model

Security should come from the language design, not from trying to filter Java
source text.

The evaluator should run only against a controlled evaluation context, for
example:

```java
record GearExpressionContext(
        Object input,
        Map<String, Object> context,
        Map<String, Object> parameters,
        StationOutcomeView stationOutcomes) {
}
```

Function calls should be resolved from a registry of explicitly allowed
functions. No arbitrary Java method call should be possible.

## XML integration direction

Trusted XML can keep Java inline as an advanced developer feature.

Untrusted XML should use Gear4J expressions instead:

```xml
<condition expression="input.amount > param('threshold')" />
```

The XML translator would validate and store the expression model, then generate
trusted Java source or evaluate the expression directly at runtime.

## BO integration direction

The BO should never write arbitrary Java. It should build expression ASTs through
structured UI controls and serialize them as the Gear4J expression language or as
a typed expression JSON model.

## Open questions

- Should expressions be interpreted at runtime or compiled to generated Java?
- Should the syntax be text-first, JSON-AST-first, or both?
- How should path access work for Java records, maps and beans?
- Should the expression language support custom user functions?
- How should type errors be reported during pipeline validation?

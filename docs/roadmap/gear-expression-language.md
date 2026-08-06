# Gear4J expression language

## Status

MVP parser/evaluator and XML condition integration are implemented in
`io.github.gear4jtest.xml.expression`. Property access is secure by default and
defined by ADR 0018; the original language-boundary decision is captured in ADR
0008.

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

Numeric equality is based on the represented decimal value rather than the Java
wrapper type. `Byte`, `Short`, `Integer`, `Long`, `BigInteger`, `Float`,
`Double` and `BigDecimal` therefore compare consistently; `BigDecimal` scale is
ignored and signed zeros are equal. `NaN` is unequal to every value, including
itself. Positive and negative infinity are equal only to an infinity with the
same sign. Custom `Number` subclasses are not inert GEL scalars and are
rejected. Non-numeric scalars retain their normal Java equality semantics.

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

Implemented property-access rules:

- secure contexts read inert map snapshots only;
- records and JavaBeans require an exact-type/property allowlist;
- approved records can be converted to immutable value trees before evaluation;
- a deprecated legacy policy supports migration with warnings;
- accessor lookup uses `ClassValue` and cached `MethodHandle` instances.

## XML integration direction

Trusted XML can keep Java inline as an advanced developer feature.

Untrusted XML should use Gear4J expressions instead:

```xml
<condition language="gel" expression="input.amount > param('threshold')" />
```

The XML translator validates the GEL expression at generation time and emits a generated helper that evaluates it at runtime without exposing arbitrary Java execution.

## BO integration direction

The BO should never write arbitrary Java. It should build expression ASTs through
structured UI controls and serialize them as the Gear4J expression language or as
a typed expression JSON model.

## Open questions

- Should expressions remain interpreted at runtime or gain an optional compiled form?
- Should the syntax be text-first, JSON-AST-first, or both?
- Should allowlists support sealed hierarchies without weakening exact-type matching?
- Should the expression language support custom user functions?
- How should type errors be reported during pipeline validation?

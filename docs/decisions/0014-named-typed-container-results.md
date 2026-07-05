# Named typed container results

## Status

Accepted.

## Context

The original typed container API provided strong function typing for one and two
branches through arity-specific wrappers. Beyond two branches, extending the
same pattern would require `Container3Station`, `Container4Station`, and so on,
or would force users back to positional `Object...` aggregation.

A positional varargs aggregator is compact but fragile:

- branch order becomes part of the hidden contract;
- inserting a branch in the middle changes aggregator semantics;
- skipped/failed branches produce `null` slots that are easy to misread;
- more than two branches cannot remain cleanly type-safe without arity-specific
  classes.

## Decision

Introduce a named-results container model instead of adding more arity-specific
container classes.

New code should define typed container branches from the station itself:

```java
var price = Stations.branch("price", priceStation);
var stock = Stations.branch("stock", stockStation);
```

Then build containers with those branch handles:

```java
Stations.container("product-enrichment", Product.class)
        .withBranch(price)
        .withBranch(stock)
        .returns(results -> new ProductEnrichment(results.get(price), results.get(stock)));
```

The container still preserves branch declaration order internally for trace
compatibility, but aggregation no longer needs to depend on positional
`Object...` values. `ContainerResults` exposes both named and ordered views so
legacy code and diagnostics can coexist.

## Compatibility

Because the library is still pre-1.0, the arity-specific one/two-branch wrappers
and the legacy `ContainerFunction<Object...>` aggregation path are removed. The
single supported container model is now `ContainerBranch` plus
`ContainerResults`, which works the same way for one, two or many branches.

## Consequences

- No `Container3Station`/`Container4Station` class family is introduced.
- Multi-branch aggregation becomes readable and resilient to branch reordering.
- Compile-time typing comes from the station embedded in `ContainerBranch<IN, OUT>`; callers do not repeat
  `Class<T>` tokens for normal access.
- `IGNORE`/flow-signal semantics are unrelated to this design and remain handled
  by the signal taxonomy decisions.

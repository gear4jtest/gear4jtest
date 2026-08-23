# 0045 - Extension selection and store identifiers are explicit

## Status

Accepted - 2026-08-23

## Context

Three extension paths depended on `ServiceLoader` iteration order. Duplicate
artifact-store types overwrote each other, translator probe failures were
discarded and both translators and compilers selected the first matching
provider. Repackaging an application could therefore change behavior without a
configuration change.

The artifact-store SPI accepted arbitrary string types, but the public
configuration model used a closed enum and the MySQL/MariaDB V1 schemas used a
database enum. A discovered third-party backend could not be represented and
persisted without changing Gear4J.

The JDBC object/publication repository also combined transaction orchestration,
object SQL, stage SQL, tag SQL and row mapping. The generated classloader kept
caller-owned mutable byte arrays even though custom compilers are part of the
SPI.

## Decision

- SPI discovery never uses classpath order as precedence.
- Artifact stores keep their canonical type as the stable selector and reject
  duplicate types at resolver construction.
- Translators and generated-source compilers expose a stable `id()`. Default
  selection rejects multiple candidates; explicit overloads select by id.
- Translator `supports(...)` failures are aggregated and fail resolution because
  a failed probe prevents Gear4J from proving that selection is unambiguous.
- No implicit numeric priority is introduced. Equal applicability is a
  configuration error, not a tie to break.
- `StoreType` becomes an open, validated value object while retaining constants,
  `name()` and `valueOf(...)` source conveniences for the built-in values.
- Every unreleased external V1 schema stores a 1-to-64-character canonical store
  type in `VARCHAR`/`VARCHAR2` with a format check. MySQL and MariaDB no longer
  enumerate built-in values in the schema.
- `OperationChainObjectRepositoryJdbc` remains the public transactional facade.
  Object, publication-stage, tag and row-mapping SQL move to package-private
  collaborators.
- `InMemoryClassLoader` clones and validates all supplied bytecode before
  publishing it to the concurrent class map.

## Consequences

Applications with more than one compiler provider must select one by id or
inject one explicitly. Applications with overlapping translators must either
remove the overlap or select the translator id explicitly. Provider failures
now surface during resolution instead of being reported later as an unrelated
"no provider" result.

Code that treated `StoreType` as an enum must migrate before 1.0: replace
`switch`, `values()` and enum-only reflection with equality against built-in
constants or `StoreType.of(...)`. Third-party plugins can now use the same
identifier in Java configuration, JDBC persistence and fallback declarations.

The initial external schema changed in place because Gear4J has not shipped a
public release. Development databases created from an older V1 must be
recreated. No V2 migration is introduced for an unreleased schema.

The JDBC facade retains transaction and conflict policy, keeping the atomic
publication contract reviewable. The extracted collaborators are internal and
do not enlarge the public compatibility surface.

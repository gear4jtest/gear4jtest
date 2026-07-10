# 0020 - Health probes and metric cardinality are bounded

## Status

Accepted.

## Context

The original persistence health indicator derived current health from cumulative
failed-flush and rejected-append counters. One transient incident therefore kept
an application `DOWN` after the database and backlog had recovered. The same
observability surface emitted raw pipeline, operation and branch IDs, making the
number of Micrometer series dependent on user-controlled identifiers.

## Decision

Persistence exposes separate liveness and readiness semantics.

- Liveness is process-local and must never call an external system.
- Readiness runs a provider-specific JDBC validation query with a configured
  timeout, then checks current backlog size, oldest backlog age and whether a
  failed flush still has unrecovered records.
- Cumulative failures and rejections remain metrics and diagnostic details; they
  are not permanent health state.
- A later successful flush records recovery and permits readiness to return to
  `UP` when the backlog is within configured limits.

The default Micrometer tag policy omits all identifiers. Only bounded status tags
are retained on completed lifecycle metrics. Identifier tags require an explicit
finite allowlist, which maps unknown values to `other`, or a custom policy. The
historic raw-ID policy is deprecated for removal.

Event and reaction drops remain aggregate tagless metrics.

## Consequences

Database outages remove an instance from readiness without causing a liveness
restart loop, and recovered instances automatically return to service. Operators
must configure health groups deliberately and tune backlog thresholds to their
traffic and recovery objectives.

Default dashboards lose per-pipeline and per-operation dimensions. Applications
that require them must define and review a cardinality budget through an
allowlist or custom tag policy.

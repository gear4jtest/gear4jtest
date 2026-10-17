# 0051 — Studio observes runs without orchestrating deployments

Status: accepted for experimental Studio P4.

## Decision

Bound controlled-test receipts by terminal TTL/LRU, preserving all active
receipts. Keep their process-local idempotence window distinct from durable run
history. A storage failure after execution is an uncertain outcome, never proof
that the chain did not execute.

Give each publication/promotion retry an immutable attempt ID and current actor
and time. A completion can update only the matching current attempt. A refresh
also compares its starting snapshot. Preserve the native TEST/RUN lifecycle.

Represent one logical catalogue capability with separate TEST/RUN implementation
bindings. Validate Java contracts and parameters at application startup; use
the same manifest for source/capability policies. Serialize logical parameter
tokens and resolve them only inside the versioned Studio XML adapter. Record
schema and adapter provenance alongside exact immutable artifact bytes.

Persist sanitized, bounded run snapshots in a dedicated management journal.
Journal identity, engine run identity and transient request receipt identity
are distinct. Always retain exact known revision/version/build/catalogue
provenance. Unknown host provenance stays unknown; programmatic chains can be
observed without acquiring a declarative definition.

Expose read APIs through the secured facade; keep host ingestion as a separate
trusted port. Evaluate current resource ACLs per observation. Separate history
permission from observation-payload permission, and never aggregate private runs
into another reader's metrics. Bound scan work and disclose partial results.

Use best-effort SSE snapshots and reload on reconnect, with polling fallback.
Do not add a durable event stream, generic command workflow, deployment binding
or multi-target control plane. The host owns journal retention and runtime
resources; no background execution retry or stale-process reconciliation.

## Consequences

P4 metrics describe retained accessible observations, not universal engine
traffic. A process interruption can leave an active-looking last observation;
the UI states this limit. No arbitrary error, log, context or payload object is
copied into the journal. Capture policy remains an explicit application choice.

Reusable scenarios move explicitly to P4.1, with a separate fixture-admission
policy; masking a captured value does not create an equivalent test fixture.
Simulation/mocks remain P6 and distributed management remains P5.

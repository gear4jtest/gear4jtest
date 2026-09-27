# 0047 - Studio P0 is a local management proof

## Status

Accepted for prototype - 2026-09-26. Public API stability: Experimental.

## Decision

Management contracts and the declarative subset live outside core. XML is an adapter and canonical executable artifact for the prototype. Existing validation, generation, compiler, loader, injector and executor are reused.

Explicit operation registrations drive both metadata and executable capabilities. Nested operation classes are rejected because the current generator emits invalid imports for them. Parameters use registered getters, and resource parameters admit only approved references. Capture is off by default and suppressed for resource-bearing chains.

Draft revisions and test receipts are bounded and process-local. Tests are synchronous real executions with cooperative cancellation, without implicit publication. No deployment, distributed command or durable event-stream model is introduced.

The two hosts share public contracts and one reusable component. This establishes an integration boundary without freezing the production frontend technology. Demo Maven publishing is disabled.

One scoped unchecked cast bridges GeneratedAssemblyLine to the String envelope, after contract and javac checks. Existing suppression budgets and coverage thresholds are not weakened.

## Consequences

Unsupported XML fails closed. Invalid structured drafts may be retained, but unsafe definitions may not. The HTTP/authentication demonstration is not a production endpoint. Programmatic-chain observation, durable ACL/audit and TEST/RUN publication remain subsequent work.

See the [P0 guide](../studio/p0.md) and [verification report](../studio/verification.md).

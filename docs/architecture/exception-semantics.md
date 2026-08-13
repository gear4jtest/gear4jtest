# Exception semantics

Gear4J uses dedicated unchecked exceptions when a failure is part of a stable
public or SPI contract, and `IllegalStateException` for invalid construction or
internal invariant violations.

## Dedicated contract exceptions

- `StationExecutionException`: wraps operator failures at station boundaries.
- `AssemblyLineCallException`: reports invalid assembly-line call execution.
- `AssemblyLineCancellationException`: carries cooperative cancellation.
- `PayloadCloneException`: reports payload isolation failures.
- `ConcurrentTransformerUseException`: reports worker-lock acquisition/release
  failures for protected worker instances.
- `ExecutionPersistenceException`: reports durable persistence/SPI failures.
- `StationLifecycleException`: records critical lifecycle observer failures.

## Accepted `IllegalStateException` usage

`IllegalStateException` remains acceptable when the failure is not a reusable
public contract type but an invalid builder/runtime state, for example:

- invalid inline runtime contract combinations;
- missing internal strategy registration;
- impossible non-terminal execution statuses at result mapping time;
- missing optional metadata requested as mandatory;
- side-compute access before a value has been resolved.

## Fatal JVM errors

Recoverable engine and station boundaries catch `Exception`, not `Throwable`.
Once a run has started, ordinary exceptions are normalized into an
`ExecutionResult` and terminal traces. Error-policy and lifecycle failure modes
also apply only to non-fatal exceptions.

An `Error` thrown by user code, an extension or a parallel branch is rethrown as
the same fatal value. It is not wrapped in `StationExecutionException`, is not
matched by station error policies and does not invoke normal run-completion
hooks. Run-scoped cleanup in `finally` blocks is still attempted. This boundary
prevents `OutOfMemoryError`, linkage failures and similar JVM failures from being
misrepresented as recoverable business outcomes.

## Signal stations

Explicit flow-signal stations only support `SignalType.STOP` and
`SignalType.FATAL`. `SignalType.IGNORE` is reserved for error policies, where it
means “ignore this matching error according to the configured policy”. XML uses
separate schema enums for these two contexts: flow signals are `STOP/FATAL`,
while error signal policies remain `STOP/FATAL/IGNORE`.

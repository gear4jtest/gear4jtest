# 0046 - Exception logs require explicit detail opt-in

## Status

Accepted - 2026-09-12

## Context

Persistence capture can discard or redact sensitive values while ordinary SLF4J
exception rendering still prints them from messages, causes, suppressed
exceptions or user-supplied stack frames. Changing a log level does not remove
this exposure. Audit finding F07 identified the paths; it did not establish an
actual credential disclosure.

## Decision

- Framework log calls pass exceptions through `ExceptionDiagnostics.forLogging`.
- The default replaces the exception with its class name and a withheld-details
  indication, with no stack or original graph reference. It does not inspect
  potentially user-defined message/rendering methods.
- Original execution failures remain available in results, traces and rethrows.
  Log confidentiality does not change failure precedence or recovery behavior.
- Full exception rendering requires the process-wide JVM property
  `gear4j.logging.includeExceptionDetails=true`. Missing or invalid values and
  denied property reads retain the protected default. The property is read when
  the log argument is prepared, so a diagnostic window may be changed explicitly.
- This property is independent from Boot persistence redaction settings and
  event payload policies. Raising a logger to DEBUG does not opt in.
- Core keeps SLF4J as its only logging dependency; no logging backend is added
  to published runtime dependencies. The regression source uses the existing
  test-only Logback dependency to inspect rendered exception chains.

## Consequences

Default logs identify the callback, existing correlation metadata and exception
class but omit stack-level diagnosis. Applications needing full diagnostics must
explicitly enable details with reviewed access/retention, or log an authorized
execution result through an application-owned channel. Non-critical hooks whose
failures are only logged require that explicit diagnostic mode for full stacks.

Protection does not scrub arbitrary application/dependency logs, MDC or
identifiers. Code must not place secrets in those fields. Historical logs are
not rewritten by this change. Tests distinguish preservation of the original
consumer failure from the sanitized diagnostic sent to the logger.

The setting is deliberately not coupled to a per-run persistence configuration:
shared dispatchers and cleanup callbacks also log outside an individual run.
There is no implicit Spring property binding or backend-specific redaction rule.

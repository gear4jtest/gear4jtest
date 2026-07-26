# 0033 — XML operator capabilities are explicit and mode-aware

## Status

Accepted.

## Context

The GEL-only XML path rejected inline Java and restricted expression property
access, but a `processingOperation` could still select any visible
`Operator` class through its `type` attribute. An operator is executable
application code and may expose capabilities that GEL itself correctly forbids,
such as database, file, network or process access.

TEST and RUN also have different trust surfaces. Validating a TEST definition
against TEST-only application code does not prove that the same definition is
eligible for RUN.

## Decision

Restricted XML treats `processingOperation/@type` as a stable functional
capability identifier. Trusted application configuration maps each identifier
to an operator class separately for `ExecutionMode.TEST` and
`ExecutionMode.RUN`.

`XmlOperatorCapabilityPolicy` is deny-by-default. The default
`ServiceLoader` translator has no registered operator capabilities.
`XmlOperationChainTranslator.gelOnly(policy)` is the explicit restricted
configuration path.

The external translator SPI receives `ExecutionMode`. Direct publication,
promotion and runtime loading pass the actual object mode. Promotion translates
and compiles the stored artifact again with RUN capabilities before RUN metadata
is staged.

`XmlOperationChainTranslator.trusted()` remains an explicit escape hatch for
reviewed definitions. In that mode, the XML value remains a Java class name and
inline Java may be emitted.

## Consequences

- A BO-authored definition cannot select a Spring-visible or classpath-visible
  operator unless trusted configuration registered its capability id.
- TEST-only capabilities cannot pass RUN promotion validation.
- The same functional id can map to different TEST and RUN implementations.
- All nested processing operations are resolved before Java rendering.
- Existing restricted XML that used Java class names must move those mappings
  into the capability registry. This is an intentional pre-1.0 security
  hardening.
- Custom non-XML translators remain source compatible through the SPI default
  method when they do not need mode-aware behavior.

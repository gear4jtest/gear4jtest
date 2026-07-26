# XML security boundary

Gear4J XML generation can embed Java expressions into generated source code.
This is powerful, but it means trusted XML is effectively code.

Restricted XML has two independent security boundaries:

- GEL controls which expressions can inspect data;
- the operator capability policy controls which application code the definition
  can invoke.

Both must be restrictive. GEL alone is insufficient because an arbitrary
`Operator` class can expose file, network, database or process capabilities
without using an XML expression.

## Default policy: deny inline Java and all operators

The default translator discovered through `ServiceLoader` rejects inline Java
and starts with an empty operator capability allowlist. A restricted translator
must be configured by trusted application code:

```java
var capabilities = XmlOperatorCapabilityPolicy.builder()
        .allow("customer.normalize", NormalizeTestCustomer.class, ExecutionMode.TEST)
        .allow("customer.normalize", NormalizeCustomer.class, ExecutionMode.RUN)
        .allowInAllModes("address.validate", ValidateAddress.class)
        .build();

var translator = XmlOperationChainTranslator.gelOnly(capabilities);
```

Restricted XML uses the stable capability id, not a Java class name:

```xml
<processingOperation id="normalize" type="customer.normalize"/>
```

The generator resolves every nested processing operation before rendering Java.
Unknown identifiers and identifiers not allowed in the requested
`ExecutionMode` fail with `SecurityException`. Direct TEST publication,
promotion to RUN and runtime loading all pass their actual mode into the
translator, so a TEST-only capability cannot be promoted or loaded as RUN.

The same capability id may intentionally map to different operator classes in
TEST and RUN. This keeps environment-specific implementation choices in trusted
configuration instead of in user-authored XML.

## Trusted XML

Trusted XML must now opt in explicitly:

```java
XmlOperationChainTranslator.trusted();
```

Trusted mode preserves XML class-name resolution and inline Java:

```xml
<processingOperation id="normalize" type="com.example.NormalizeCustomer"/>
```

This mode is intended only for XML authored and reviewed by trusted developers or
by a trusted build process. It is equivalent to accepting Java source into the
application JVM and is not a sandbox.

## Threat model

| Definition-controlled input | Restricted-mode control | Residual trust |
| --- | --- | --- |
| Inline Java expressions | Rejected by `XmlJavaSourcePolicy` | Trusted mode executes reviewed Java |
| GEL expressions | Restricted AST and property policy | Custom property policies are trusted code |
| Operator reference | Exact capability allowlist per TEST/RUN | Registered operator implementation is trusted code |
| Java class-name bypass | Not present in the allowlist, therefore rejected | Trusted mode accepts class names deliberately |
| Nested iterator/container/if-else operators | Recursively resolved before generation | None beyond registered capabilities |
| Promotion TEST to RUN | Candidate is translated again with RUN capabilities | Publication repository and application policy |
| Dependencies injected into generated code | Existing mode-aware dependency injector | Registered dependency instances are trusted |
| XML entities and external schemas | Hardened parser/validator configuration | JDK XML implementation |
| Oversized XML | Pre-XSD byte limit | Configured size budget |

## Why Gear4J does not provide "safe Java inline" today

A regex or simple whitelist around Java source would create a false sense of
security. Safe Java inline would have to prevent direct and indirect access to
APIs such as reflection, process execution, file system, network, class loading,
static imports and arbitrary method calls.

For that reason, Gear4J currently exposes two honest modes:

- trusted Java/class-name source generation;
- GEL-only plus operator capability allowlists for restricted definitions.

It does not claim to sandbox arbitrary Java snippets inside the same JVM.

## Gear4J Expression Language

GEL is a dedicated expression language for BO and untrusted definitions. It is
parsed into a controlled AST and evaluated by Gear4J without arbitrary Java
access. The current XML integration supports GEL for conditions:

```xml
<condition language="gel" expression="input.status == 'ACTIVE'"/>
```

The MVP language supports literals, paths such as `input.foo` and
`variables.foo`, equality/inequality, boolean operators and parentheses. It does
not support Java method calls, constructors, type lookup, class literals, static
access, reflection, I/O or networking. Reflective Java metadata properties such
as `class`, `getClass` and `metaClass` are rejected on object paths; map keys with
those names remain regular data keys. The default context snapshots maps and
rejects Java object properties. Trusted applications can explicitly allow exact
record/JavaBean types and properties with `PropertyAccessPolicy`; untrusted
definitions should receive an immutable tree created by
`GearExpressionValues.snapshot(...)`.

Generated XML uses the secure default policy. A GEL expression that navigates a
rich Java input must therefore receive an upstream inert map representation.
The deprecated `legacyBeanAccess()` policy is intended only as a temporary
migration aid for direct evaluator users and logs every newly used accessor.

See [Gear4J expression language](../roadmap/gear-expression-language.md).

## Input size boundary

`AssemblyLineValidator` applies a configurable byte limit before XSD processing. Both byte-array and stream entry points
use the same default of 2 MiB; stream validation reads at most `maxXmlBytes + 1` bytes before rejecting oversized input.
Applications accepting untrusted XML should keep this limit finite and align it with the external artifact-size policy.

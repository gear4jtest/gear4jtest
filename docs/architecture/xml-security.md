# XML security boundary

Gear4J XML generation can embed Java expressions into generated source code.
This is powerful, but it means trusted XML is effectively code.

## Default policy: untrusted / no inline Java

The default generator policy is restrictive:

```java
new XmlToJavaGenerator();
```

This rejects inline Java expressions and is the right default for untrusted XML,
user-edited XML or BO-authored XML. Conditions can still be expressed with the
restricted Gear4J Expression Language by setting `language="gel"` on
`<condition>` elements.

## Trusted XML

Trusted XML must now opt in explicitly:

```java
XmlToJavaGenerator.trusted();
```

or, for custom package/formatter settings:

```java
new XmlToJavaGenerator(
        "io.example.generated",
        Thread.currentThread().getContextClassLoader(),
        JdtFormatter.defaultFormatter(),
        XmlJavaSourcePolicy.trusted());
```

This mode is intended only for XML authored and reviewed by trusted developers or
by a trusted build process. The restrictive policy is also available explicitly
through `XmlJavaSourcePolicy.forbidInlineJava()`.

## Why Gear4J does not provide "safe Java inline" today

A regex or simple whitelist around Java source would create a false sense of
security. Safe Java inline would have to prevent direct and indirect access to
APIs such as reflection, process execution, file system, network, class loading,
static imports and arbitrary method calls.

For that reason, Gear4J currently exposes two honest modes:

- trusted Java source generation;
- GEL-only/no-inline-Java for untrusted definitions.

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
those names remain regular data keys.

See [Gear4J expression language](../roadmap/gear-expression-language.md).

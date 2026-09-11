# Phase 2 hotfix — restricted XML task dispatch

| Field | Value |
| --- | --- |
| Status | Implemented; Gradle execution verification pending |
| Owner | Gear4J maintainers |
| Last reviewed | 2026-09-10 |

Baseline: the delivered `gear4jtest-20260908-phase-2.zip`, SHA-256
`cf696b595f0dd786b74985571f680d0a5045cf4520d159bfed2f6a84ee72363e`.
This is a corrective update to phase 2. Phases 3 and 4 remain planned.

## Reported failure and diagnosis

The user reported a TestKit build failure while running
`xmlGenerateAssemblyLine --configuration-cache --configuration-cache-problems=fail
--build-cache --warning-mode=all --stacktrace`:

```text
Could not find method restrictedCapabilities() for arguments [] on task
':xmlGenerateAssemblyLine' of type
io.github.gear4jtest.xml2java.XmlAssemblyLineGenerateTask.
```

In `XmlAssemblyLineGenerateTask.translateSources`, phase 2 moved the call to
the private instance method `restrictedCapabilities()` into `withCloseable`.
The diagnostic indicates that this closure dispatch reaches Gradle's decorated
task instead of the private implementation method. This diagnosis follows from
the supplied error and the source change; it has not been reproduced locally.
Groovy documents method resolution through closure owners and delegates in its
[closure delegation reference](https://docs.groovy-lang.org/latest/html/documentation/core-closures.html#_delegation_strategy).

## Correction

- Read `trustedXml` once and construct the restricted capability policy in the
  enclosing method before entering `withCloseable`.
- Capture that local policy in the closure. Keep the helper private.
- Construct the translator after installing the operator context classloader,
  as required by the phase 2 operator-classpath support.
- Preserve restricted mode by default, explicit trusted mode, context-loader
  restoration, classloader closure and output replacement after full validation.
- Keep all dependency versions, Gradle wrapper settings and phase 1/2 fixes.

The trusted branch does not build the unused restricted policy. Its local policy
variable is null, but that branch calls `trusted(...)` and never passes it to
`gelOnly(...)`.

## Regression coverage

The existing `XmlAssemblyLineGeneratorFunctionalTest` already exercises the
failing default-mode path with configuration and build caches enabled. Its
configuration is intentionally left in restricted mode.

Added `generation_shouldKeepRestrictedPolicyWhenReusingConfigurationCache`:

1. Generate a valid signal-only pipeline with default settings.
2. Change the XML to request an unregistered operator.
3. Reuse the configuration cache and require the capability-specific rejection.
4. Check that the last successfully generated source remains intact.

Existing module tests cover registered capabilities, trusted XML, inline Java
rejection, classloader restoration and consumer operator classpaths.

## Validation on 2026-09-10

Source review and `git diff --check` passed. The living-documentation metadata
rules were replayed locally on the ten documents in the root task's scope and
passed. Archive integrity, retained baseline files and executable wrapper mode
were checked during packaging; the delivered archive excludes repository history
and transient Gradle outputs.

The following commands were attempted after the correction:

```bash
bash gradlew --no-daemon :gear4jtest-gradle-xml2java:test
bash gradlew --no-daemon spotlessApply
bash gradlew --no-daemon check
```

All exited with code 1 while downloading Gradle 9.6.1, before build startup:

```text
Downloading https://services.gradle.org/distributions/gradle-9.6.1-bin.zip
Attempt 1/1 failed. Reason: Network is unreachable
java.net.SocketException: Network is unreachable
```

No local Gradle/Groovy distribution is available. The modified Groovy sources
and TestKit regression have therefore not been compiled or executed here.
Passing runtime verification, formatting and the full build remain required.

## Commit message

```text
fix(gradle): resolve restricted capabilities outside task closure
```

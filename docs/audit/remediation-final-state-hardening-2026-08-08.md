# Final state hardening — 8 August 2026

**Findings:** C-02, C-03 and DOC-01.

## Implemented contract

- `WhitelistedContextFingerprintStrategy` validates its constructor dependencies
  and freezes the whitelist with a defensive copy before publication.
- Mutating the caller-owned list after construction, including concurrently with
  fingerprint calculation, cannot change tenant/context separation or trigger a
  concurrent iteration failure.
- `AssemblyLineCallStack` distinguishes an absent worker-thread value from an
  explicitly installed empty snapshot.
- Closing a propagated scope removes an initially absent `ThreadLocal` value,
  while nested scopes restore the exact previous state.
- Release-build policy is ADR 0035; generated-code budgets remain ADR 0031.
- `verifyDecisionIdentifiers` rejects malformed ADR filenames and duplicate
  four-digit identifiers from both `check` and `releaseMetadataCheck`.

## Regression coverage

- post-construction and concurrent mutation of the original whitelist;
- null whitelist, null key and null delegate rejection;
- 10,000 call-stack snapshot scopes on one reused worker thread;
- ordinary and nested call-stack restoration;
- repository-wide ADR identifier verification.

The connected build remains the release gate:

```bash
./gradlew spotlessApply
./gradlew :gear4jtest-experimental-cache:test
./gradlew :gear4jtest-core:test
./gradlew verifyDecisionIdentifiers verifyDocumentationLinks
./gradlew check
```

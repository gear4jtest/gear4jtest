# Remediation R5 — Filesystem artifact-store hardening

## Scope

This phase closes the filesystem portion of audit findings F-11 and F-22. It
does not change artifact identifiers or the `ArtifactStore` content-addressed
contract.

## Implemented controls

- The configured root is absolute, normalized, created eagerly and revalidated
  before every operation.
- Existing symbolic links are rejected in the root path, hash directories and
  artifact leaf.
- Root/hash directories use owner-only POSIX permissions (`0700`) and temporary
  plus final files use `0600` where POSIX permissions are available.
- Publication no longer performs an `exists`/replace sequence. A move that
  encounters an existing immutable target verifies that target instead of
  overwriting it.
- Reads accept only regular files opened with `NOFOLLOW_LINKS`, verify SHA-256
  and expose bytes only after the verification succeeds.
- Temporary-file cleanup failures are counted in `ArtifactStoreStats` and
  logged at counts 1, 2, 4, 8 and subsequent powers of two.

## Operational contract

The root must be owned by the application and its parent directories must not be
writable by unrelated users. Local users with the same operating-system
identity as the application remain inside the application's trust boundary.
Verified filesystem reads return an in-memory snapshot; keep the manager/store
artifact-size limit aligned with the JVM memory budget.

## Regression coverage

Tests cover root, ancestor, hash-parent and leaf symlinks; post-construction root
replacement; tampered bytes; verified snapshot stability; POSIX permissions;
size-limit cleanup; and observable cleanup failure.

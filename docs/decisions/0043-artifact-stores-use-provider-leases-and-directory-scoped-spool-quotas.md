# 0043 - Artifact stores use provider leases and directory-scoped spool quotas

## Status

Accepted - 2026-08-22

## Context

An assembly-line manager cached one store per assembly-line identifier without
a capacity or close contract. The consistency checker and publication
reconciler constructed additional stores directly from the provider. This could
produce different in-memory backends for the same configuration and could leak
temporary-spool resources after configuration changes.

The managed spool also accounted quota in each Java object even when several
objects wrote to the same directory. Two managers could therefore each accept
the full configured quota. Separate JVMs could concurrently scan and delete the
same residues.

## Decision

- `ArtifactStore` is `AutoCloseable` with a no-op default. Implementations close
  only resources they own; an externally supplied data source or executor
  remains application-owned.
- `ArtifactStoreProvider.forConfig(...)` returns a lease.
  `ArtifactStoreProvider.release(...)` returns that lease and defaults to a
  no-op for compatibility with application-owned providers.
- `DefaultArtifactStoreProvider` shares one store for equivalent store type and
  property maps while leases remain active. It closes the store after the final
  release. The manager, checker and reconciler balance every acquisition.
- The manager resolver uses a 256-entry access-ordered cache. Replacement,
  explicit invalidation, LRU eviction and manager shutdown release provider
  leases. Identity reference counting prevents a shared store from being
  released while another assembly line still references it.
- The in-memory store rejects new distinct content after a finite 5 MiB
  per-artifact, 64 MiB total or 10,000-entry default limit. Duplicate content is
  idempotent and consumes no additional capacity. Explicit constructor and
  `MEMORY` properties allow different reviewed limits.
- Spool occupancy and quota counters belong to a canonical directory, not a
  store instance. Live instances must use the same policy for that directory.
  A process lock prevents an explicitly configured directory from being shared
  across JVMs. The default directory is isolated per JVM runtime.
- Failed writes reconcile accounting with the file's actual size instead of
  assuming that an exceptional bulk write wrote zero bytes.

## Consequences

Applications using `DefaultArtifactStoreProvider` directly must pair every
`forConfig(...)` call with `release(...)`, or close the provider after all of its
consumers have stopped. `AssemblyLineManager.close()` now performs this cleanup
for manager-held leases. Manager invalidation and close must not race with
application calls using the same manager.

The spool directory contains a private `.gear4j-spool.lock` marker. Operators
must configure a dedicated directory per process or container; accidental
cross-process sharing fails fast. Multiple stores inside one JVM safely share
the directory and one global quota.

The `MEMORY` backend remains suitable only for tests and small single-JVM
deployments. Capacity exhaustion is reported rather than evicting referenced
content, because silent eviction would invalidate durable metadata.

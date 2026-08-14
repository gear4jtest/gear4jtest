# Phase 6 — CI, observability and release readiness

**Date:** 2026-08-14

**Scope:** CI/release workflow contracts, release-version and source-ref policy,
qualification-evidence retention, release documentation and a final review of
the existing observability boundary. This phase does not change production Java
code, public APIs, dependency policy or runtime metrics.

## Executive outcome

The source tree is ready to enter a connected release-candidate qualification
run. It is not, by itself, proof that version 1.0 is releasable: the authoritative
database matrix, `releaseCheck`, reproducibility rebuild and JReleaser dry run
must still succeed on the exact candidate commit. The workflow now emits a
commit-bound manifest that makes this distinction explicit and auditable.

Four release-process gaps were confirmed and corrected:

- ordinary CI did not execute the release-metadata gate documented as part of
  CI, and `releaseCheck` did not depend on it;
- a manually dispatched non-dry release could start from an arbitrary branch,
  and version/tag consistency was not validated before the database matrix;
- the reproducibility script ran `clean` and erased reports produced by the
  qualified build before artifacts were uploaded;
- the workflow produced artifacts and logs, but no manifest binding those
  results to a version, commit, ref, run and accepted risk.

The runtime observability implementation reviewed in the earlier phases is
already proportionate to the library boundary. No unproven metric, tracing
backend or application health endpoint was added merely to expand the feature
list.

## Confirmed findings

### Release metadata was outside the effective aggregate gate

**Severity:** High
**Category:** Build / Configuration
**Location:** `.github/workflows/ci.yml`,
`build-logic/src/main/groovy/gear4j.root-release.gradle` (`releaseCheck`)
**Finding:** Documentation stated that ordinary CI and release validation ran
`releaseMetadataCheck`. CI only ran `clean build coverageReport`, and
`releaseCheck` did not depend on the metadata task. A broken local documentation
link, legal-file error, ADR metadata collision or invalid isolated JReleaser
model could therefore escape the advertised aggregate gates.
**Impact:** The documented release procedure was stronger than the executable
one, which is particularly risky for a first Maven Central release.
**Correction:** CI now invokes `releaseMetadataCheck`, and `releaseCheck`
depends on it. A workflow-contract task asserts both fragments.
**Priority:** P1

### Release source and version were insufficiently constrained

**Severity:** High
**Category:** Security / Configuration
**Location:** `.github/workflows/release.yml`,
`scripts/validate_release_invocation.py`, `verifyReleaseVersion` in
`gear4j.root-release.gradle`
**Finding:** Tag patterns accepted broad strings and manual dispatch could run a
credentialed non-dry publication from a feature branch. There was no single
preflight check requiring an exact `v<version>` tag on tag pushes or a
non-snapshot SemVer-like version.
**Impact:** Operator error could publish unintended coordinates or code from an
untrusted project ref. This control limits accidental misuse; it is not a
security boundary against a maintainer able to modify the workflow itself.
**Correction:** A prerequisite job now validates the version and ref before the
four database jobs. Tag pushes require an exact match. Manual dry runs remain
available from any selected ref; manual publication is restricted to the
default branch or exact matching tag. Gradle independently rejects snapshot or
malformed release versions.
**Priority:** P1

### Reproducibility cleanup deleted qualification reports

**Severity:** High
**Category:** Build / Observability
**Location:** `scripts/verify-reproducible-staging.sh`
**Finding:** The second staging build intentionally executes `clean`, which
removed JUnit XML/HTML, OWASP Dependency-Check, Japicmp and JMH reports produced
by the first `releaseCheck`. The upload step consequently could not retain the
complete evidence for the build that had passed the gate.
**Impact:** A successful release run could be difficult to audit or diagnose;
test, security, compatibility and performance evidence was lost before final
artifact collection.
**Correction:** The script preserves the relevant reports in its existing
temporary directory before `clean` and restores them after the independent
staging rebuild. Reproducibility inventories are still created from the two
actual staged repositories.
**Priority:** P1

### Successful gates were not bound to an immutable candidate identity

**Severity:** Medium
**Category:** Observability / Build
**Location:** `.github/workflows/release.yml`,
`scripts/write_release_evidence.py`
**Finding:** Uploaded reports had no summary binding their hashes and gate
results to the exact version, commit SHA, ref and workflow run. A source ZIP was
therefore easy to mistake for qualified release evidence.
**Impact:** Post-release review, incident diagnosis and provenance checks
required manual correlation across transient workflow state.
**Correction:** After a successful JReleaser deployment or dry run, the workflow
writes JSON and Markdown manifests containing candidate identity, run identity,
database result, gate outcomes, dry-run mode, API baseline and SHA-256 hashes of
the required reports and policy inputs. The generator fails closed on missing or
empty evidence, mismatched reproducibility inventories, missing JUnit XML,
failed database matrix, invalid version or missing Japicmp output when a
baseline was requested.
**Priority:** P2

### Stability wording contradicted the 1.0 release target

**Severity:** Medium
**Category:** Documentation / API
**Location:** `README.md`, `gear4jtest-experimental-cache/README.md`
**Finding:** API/module wording still said elements could disappear “before
1.0” while the same repository prepared and documented a 1.0 publication.
**Impact:** Consumers could not tell which packages were intended as stable and
whether the experimental cache artifact was published or supported.
**Correction:** The root README now links stability to `@PublicApi`/`@Spi` after
1.0 and keeps `@ExperimentalApi`/`@InternalApi` outside compatibility promises.
The cache module is documented as a published but experimental adapter-isolated
artifact.
**Priority:** P2

## Implemented corrections

- Added a tested release-invocation policy for tag, version, event, default
  branch and dry-run handling.
- Added `verifyReleaseVersion` and made the aggregate `releaseCheck` depend on
  both that task and `releaseMetadataCheck`.
- Added a five-minute release preflight job and made both the database matrix
  and publication job consume its validated outputs.
- Added Python policy tests to ordinary CI and a Gradle workflow-contract check
  to prevent the new gates from silently drifting out of YAML.
- Preserved test, vulnerability, API-compatibility and benchmark reports across
  the reproducibility script's clean rebuild.
- Added tested JSON and Markdown release-evidence generation and expanded the
  final artifact upload to include the manifest and its hashed inputs.
- Clarified the release procedure, trusted-ref rules, evidence boundary and
  compatibility wording.
- Recorded the accepted post-1.0 supply-chain risk in the release manifest
  without introducing dependency locking or Gradle verification metadata as a
  1.0 gate.

## Observability review

No production observability defect requiring a phase-6 code change was found.
The project already exposes bounded runtime statistics and optional Micrometer
integration, documents metric semantics and cardinality constraints, and keeps
Spring Boot Actuator support isolated from the core runtime. Error paths and
resource lifecycle behavior were addressed in prior phases.

Application-level trace propagation, centralized log correlation and health
policy remain host-application responsibilities. Adding a mandatory backend or
new metric series here would enlarge the public contract without evidence of a
current diagnostic gap. The new release manifest improves build-time
observability without changing runtime behavior.

## Validation performed in the audit environment

The following executable checks passed:

```text
python3 -m unittest discover -s scripts -p 'test_*.py' -v
9 tests passed

bash -n scripts/verify-reproducible-staging.sh
passed
```

Additional static validation confirmed:

- CI, release and security workflow YAML parses successfully;
- every workflow action remains pinned to a full commit SHA;
- all jobs retain numeric bounded timeouts;
- 158 Markdown files have resolvable repository-local links;
- ADR identifiers remain unique and the three JSON policy files parse;
- the release workflow contains the preflight, gate dependencies, evidence
  generation and report-upload contract required by `build.gradle`;
- the intended phase diff is limited to CI/release build logic, workflows,
  scripts and documentation; production Java sources are unchanged.

The Gradle wrapper could not download Gradle 9.6.1 because the audit environment
has no route to `services.gradle.org`. The installed Java 17 runtime also lacks
`javac`. Consequently, Groovy/Gradle compilation, Spotless, tests, dependency
analysis and staging cannot honestly be reported as executed here.

Run the authoritative connected qualification on the exact candidate commit:

```bash
./gradlew --no-daemon spotlessCheck \
  :build-logic:test \
  verifyReleaseWorkflowContract \
  releaseMetadataCheck

./gradlew --no-daemon clean releaseCheck stageMavenCentral \
  -PprojectVersion=1.0.0
scripts/verify-reproducible-staging.sh 1.0.0

PROJECT_VERSION=1.0.0 JRELEASER_DRY_RUN=true \
  ./gradlew --no-daemon jreleaserDeploy \
  -PprojectVersion=1.0.0
```

The preferred authoritative path is a manual dry run of
`.github/workflows/release.yml`, because it also proves the PostgreSQL, MySQL,
MariaDB and Oracle matrix and emits `build/reports/release/release-evidence.*`.

## Release decision and residual risks

**Decision:** source-ready for release-candidate qualification; **not yet
release-qualified** until the connected workflow succeeds for the exact commit
and its evidence manifest is retained.

Before non-dry publication, verify:

- Maven Central namespace ownership for `io.github.gear4jtest`;
- Central Portal credentials and armored GPG secrets in GitHub;
- a successful four-dialect database matrix;
- a successful `releaseCheck`, reproducibility comparison and JReleaser dry
  run;
- manifest hashes and candidate SHA correspond to the intended tag;
- the N-1 API baseline variable is configured for releases after 1.0.0.

Residual accepted risks:

- dependency locking and Gradle dependency-verification metadata remain
  deliberately deferred until after 1.0;
- the manifest is workflow evidence, not a signed software-bill-of-materials or
  external provenance attestation;
- a failure in evidence generation after a non-dry JReleaser call cannot undo a
  publication, although all publication gates have run before that point;
- repository administrators must still protect the default branch, release
  environment and secrets; those provider settings are outside the source ZIP.

## Recommended commit

```text
build: harden release preflight and preserve qualification evidence
```

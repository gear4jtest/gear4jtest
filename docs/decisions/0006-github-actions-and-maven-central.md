# Decision 0006 — GitHub Actions and Maven Central release pipeline

## Status

Accepted.

## Context

The repository is hosted on GitHub and the library is intended to be consumable as Maven artifacts.

The build must therefore provide:

- fast unit validation on pull requests;
- separated integration tests, with database lifecycles owned by Testcontainers;
- dependency vulnerability scanning;
- deterministic Maven artifact staging;
- legal files and complete Maven Central metadata in every staged publication;
- publication to Maven Central from tags or manual release workflow.

## Decision

Use GitHub Actions as the CI/CD provider.

Reasons:

- the code already lives on GitHub;
- pull requests, branch protection, checks, secrets and release tags are managed in the same platform;
- the official GitHub Java/Gradle guidance uses `actions/setup-java` and `gradle/actions/setup-gradle`;
- the Gradle action handles Gradle caching and wrapper validation.

Use JReleaser for Maven Central deployment through the Central Publishing Portal. The GitHub tag starts the workflow,
but the workflow does not create or update a GitHub Release. JReleaser 1.25 requires one enabled release provider even
for deploy-only usage, so the GitHub provider remains configured with tag creation, release creation, asset upload and
changelog generation all disabled. A scoped non-secret placeholder satisfies the provider's mandatory non-blank token
validation.

Reasons:

- the Central documentation currently states that there is no official Gradle plugin for the Central Publishing Portal;
- the same documentation lists JReleaser as a supported community option for Gradle;
- JReleaser can deploy staged Maven artifacts using the Central publisher API and can enforce Maven Central rules.

## Workflows

- `ci.yml`: release-metadata validation, unit build, integration checks, coverage and SonarQube scan on push and pull request.
- `security.yml`: scheduled/manual OWASP Dependency-Check scan.
- `release.yml`: verifies, stages and publishes artifacts to Maven Central.

## Required release secrets

The release workflow expects:

- `JRELEASER_GPG_PUBLIC_KEY`
- `JRELEASER_GPG_SECRET_KEY`
- `JRELEASER_GPG_PASSPHRASE`
- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`

The Maven Central credentials must come from a Central Portal user token.

## Versioning

Release versions are passed with `-PprojectVersion=<version>`.

Tags should use the `v<semver>` form, for example:

```text
v1.2.3
```

The Maven artifact version will be `1.2.3`.

## Release metadata and legal assets

`LICENSE`, `NOTICE` and `jreleaser.yml` are versioned release inputs. The lightweight `releaseMetadataCheck` validates
these files, repository-local Markdown links and the JReleaser model on ordinary CI changes. The complete
`releaseCheck` additionally inspects the staged JAR and POM contents before JReleaser receives credentials.

# SonarQube issue policy

Gear4J uses SonarQube Cloud as a CI-level feedback tool, not as the sole source of API or architecture policy.
Sonar issues are intentionally reviewed case by case from the Sonar UI instead of being hidden by broad
`sonar.issue.ignore.multicriteria` exclusions in Gradle.

This keeps the scanner useful for future changes: a rule can still report a real defect in new code even when a
similar existing issue is accepted for a specific reason.

Recommended workflow:

1. Fix issues that indicate real defects, unsafe behavior, flaky tests, or simple cleanup with no API impact.
2. Mark issues as Accepted / Won't Fix in Sonar only when the specific occurrence is an intentional API, DSL,
   compatibility, or architecture decision.
3. Add a short justification on the issue when the reason is not obvious.

Typical cases that may be accepted individually include semantic generic names in public APIs, phantom type
parameters used by the DSL, intentionally broad SPI contracts, or complexity hotspots already tracked in the
architecture backlog. These should remain visible and explicit in Sonar rather than suppressed globally.

# Claude instructions for Gear4J

Read `AGENTS.md` first. It is the source of truth for repository-wide agent instructions.

Also check module-level `AGENTS.md` files before editing files inside:

- `gear4jtest-core/`
- `gear4jtest-external-api/`
- `gear4jtest-xml/`

Style rules are defined by Gradle, Spotless and Checkstyle. Do not rely on your own formatting preferences.

Before producing a patch after code changes, run when possible:

```bash
./gradlew spotlessApply
./gradlew check
```

If a command cannot be run, explicitly mention the command and the reason.

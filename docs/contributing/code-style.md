# Code style

Gear4J uses a build-enforced style so humans, IDEs and coding agents produce the same result.

## Source of truth

The Gradle build is the source of truth.

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew check
```

`spotlessCheck` is wired into `check`, so `./gradlew check` and `./gradlew build` fail when checked-in source files are
not formatted.

## Java formatting

Java source is formatted by Spotless with a versioned Eclipse JDT formatter profile:

```text
config/formatter/eclipse-java-formatter.xml
```

Main conventions:

- Java 17.
- UTF-8 and LF line endings.
- 4-space indentation.
- 120-character line limit.
- Imports are normalized by Spotless.
- Method and constructor parameters stay on the declaration line while the line fits.
- When a method or constructor declaration must wrap, the first parameter remains on the declaration line and following
  parameters are aligned vertically.

## Checkstyle

Checkstyle is intentionally lightweight. It is not the formatter.

It rejects project hygiene issues such as:

- redundant or unused imports;
- JUnit 4 / TestNG / Hamcrest imports;
- string literal comparison with `==`;
- `printStackTrace()`;
- accidental `System.out` / `System.err` in production code, except legacy suppressions listed in
  `config/checkstyle/checkstyle-suppressions.xml`;

Add rules progressively. Do not enable broad rules that force unrelated refactors unless the change is intentionally a
style baseline commit.

## Javadoc policy

The build keeps Javadoc doclint enabled, except for missing-comment checks:

```text
-Xdoclint:all,-missing
```

This keeps useful validation for malformed Javadoc, invalid references and bad tags, without forcing low-value comments on
all getters, builders or obvious implementation details.

Document APIs and SPIs when the contract is important or non-obvious, especially in:

- public entry points under `core/api`;
- extension interfaces under `core/spi`;
- runtime contracts around execution, events, persistence and payload isolation;
- external pipeline loading/generation contracts.

Do not add mechanical comments such as "Gets the id" only to satisfy a tool.

## IDEs and agents

`.editorconfig` defines editor-level basics only. IDE-specific formatter preferences must not override the Gradle
formatter.

The repository includes IDE convenience files:

- `.idea/codeStyles/*` for IntelliJ IDEA project code style basics;
- `.vscode/settings.json` for VS Code Java formatter integration;
- `config/ide/eclipse/org.eclipse.jdt.core.prefs` for Eclipse formatter preferences;
- `config/ide/README.md` for IDE setup notes.

For IntelliJ IDEA, prefer one of these workflows:

1. use Gradle as the final formatter by running `./gradlew spotlessApply`;
2. optionally install an Eclipse formatter compatible plugin and point it at
   `config/formatter/eclipse-java-formatter.xml`;
3. optionally install CheckStyle-IDEA and point it at `config/checkstyle/checkstyle.xml`.

IDE integration is convenience only. The build remains authoritative.

Agents must not try to manually mimic the style. They should run `./gradlew spotlessApply` after Java edits and
`./gradlew check` before producing a patch when possible.

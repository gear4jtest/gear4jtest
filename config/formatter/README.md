# Java formatter

Gear4J uses Spotless with the Eclipse JDT formatter for checked-in Java source files.

The formatter profile is versioned in `eclipse-java-formatter.xml` so humans, IDEs and agents can share the same style.

Important choices:

- Java source level: 17.
- Maximum line length: 120 characters.
- Indentation: 4 spaces.
- Method and constructor parameters stay on the declaration line while the line fits.
- When wrapping is required, the first parameter stays on the declaration line and following parameters are aligned
  vertically.

The Gradle build remains the source of truth:

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
```

## IDE integration

IDE formatting settings are convenience only. The Gradle formatter remains authoritative.

Included convenience files:

- `.idea/codeStyles/*` for IntelliJ IDEA project code style basics.
- `.vscode/settings.json` for VS Code Java formatter integration.
- `config/ide/eclipse/org.eclipse.jdt.core.prefs` for Eclipse formatter preferences.

For IntelliJ IDEA, install an Eclipse formatter compatible plugin if you want IDE-side Java formatting to match this
profile closely. Otherwise, run `./gradlew spotlessApply` before committing.

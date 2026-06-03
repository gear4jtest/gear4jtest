# IDE configuration

Gear4J keeps the Gradle build as the source of truth for formatting and style validation.
The files in this directory and the checked-in IDE convenience files only help editors get close to the build output.

Authoritative commands:

```bash
./gradlew spotlessApply
./gradlew check
```

IDE support included in the repository:

- `.editorconfig` for shared basics across editors.
- `.idea/codeStyles/*` for IntelliJ IDEA project code style basics.
- `.vscode/settings.json` for VS Code Java formatter integration.
- `config/formatter/eclipse-java-formatter.xml` for Spotless and Eclipse/JDT-compatible IDE formatters.
- `config/ide/eclipse/org.eclipse.jdt.core.prefs` for Eclipse workspace/project formatter preferences.

If an IDE output differs from `spotlessApply`, the Gradle output wins.

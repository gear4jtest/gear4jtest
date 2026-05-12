# VS Code

The repository includes `.vscode/settings.json`.

It points the Java extension formatter to:

```text
config/formatter/eclipse-java-formatter.xml
```

with the profile:

```text
Gear4J
```

The Gradle build remains authoritative:

```bash
./gradlew spotlessApply
./gradlew check
```

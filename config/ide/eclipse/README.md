# Eclipse IDE

Use the shared formatter profile:

```text
config/formatter/eclipse-java-formatter.xml
```

or import/copy the generated preferences file:

```text
config/ide/eclipse/org.eclipse.jdt.core.prefs
```

For a workspace-level setup, import the XML formatter profile in Eclipse preferences.
For a project-level setup, copy the prefs file to the project's `.settings/` directory.

The Gradle build remains authoritative:

```bash
./gradlew spotlessApply
./gradlew check
```

# IntelliJ IDEA

The repository includes `.idea/codeStyles/codeStyleConfig.xml` and `.idea/codeStyles/Project.xml` as convenience
settings.
They cover shared basics such as indentation, line length, import layout and wrapping preferences.

For the closest Java formatting match, install an Eclipse/JDT formatter compatible plugin and import:

```text
config/formatter/eclipse-java-formatter.xml
```

The Gradle build remains authoritative:

```bash
./gradlew spotlessApply
./gradlew check
```

Do not rely on IntelliJ formatting alone before committing Java changes.

package io.github.gear4jtest.xml.generator;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdtFormatterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void fromEclipseProfile_shouldLoadTheSelectedProfile() throws Exception {
        // Given
        Path profile = temporaryDirectory.resolve("formatter.xml");
        Files.writeString(profile, profiles(
                                            profile("four-spaces", "4"),
                                            profile("two-spaces", "2")));

        JavaSourceFormatter formatter = JdtFormatter.fromEclipseProfile(profile, "two-spaces");

        // When
        String formatted = formatter.format("class Sample{void run(){int value=1;}}");

        // Then
        assertThat(formatted)
                .contains("class Sample {")
                .contains(System.lineSeparator() + "  void run() {");
    }

    @Test
    void fromEclipseProfile_shouldUseTheFirstProfileWhenNoNameIsProvided() throws Exception {
        // Given
        Path profile = temporaryDirectory.resolve("formatter.xml");
        Files.writeString(profile, profiles(profile("first", "2"), profile("second", "4")));

        // When
        String formatted = JdtFormatter.fromEclipseProfile(profile, " ")
                .format("class Sample{void run(){}}");

        // Then
        assertThat(formatted).contains(System.lineSeparator() + "  void run() {");
    }

    @Test
    void fromEclipseProfile_shouldRejectAnUnknownProfile() throws Exception {
        // Given
        Path profile = temporaryDirectory.resolve("formatter.xml");
        Files.writeString(profile, profiles(profile("available", "4")));

        // When / Then
        assertThatThrownBy(() -> JdtFormatter.fromEclipseProfile(profile, "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Eclipse formatter profile not found: missing");
    }

    @Test
    void fromEclipseProfile_shouldRejectDoctypeDeclarations() throws Exception {
        // Given
        Path profile = temporaryDirectory.resolve("formatter.xml");
        Files.writeString(profile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE profiles [<!ENTITY external SYSTEM "file:///etc/passwd">]>
                <profiles>
                  <profile name="unsafe">
                    <setting id="ignored" value="&external;"/>
                  </profile>
                </profiles>
                """);

        // When / Then
        assertThatThrownBy(() -> JdtFormatter.fromEclipseProfile(profile, "unsafe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to load Eclipse formatter profile");
    }

    @Test
    void fromEclipseProfile_shouldReportAnUnreadableProfile() {
        // Given
        Path missingProfile = temporaryDirectory.resolve("missing.xml");

        // When / Then
        assertThatThrownBy(() -> JdtFormatter.fromEclipseProfile(missingProfile, "missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unable to read Eclipse formatter profile: " + missingProfile)
                .hasCauseInstanceOf(java.io.IOException.class);
    }

    private static String profiles(String... profiles) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <profiles version="23">
                """ + String.join("", profiles) + "</profiles>\n";
    }

    private static String profile(String name, String tabSize) {
        return """
                  <profile kind="CodeFormatterProfile" name="%s" version="23">
                    <setting id="org.eclipse.jdt.core.formatter.tabulation.char" value="space"/>
                    <setting id="org.eclipse.jdt.core.formatter.tabulation.size" value="%s"/>
                    <setting id="org.eclipse.jdt.core.formatter.indentation.size" value="%s"/>
                  </profile>
                """.formatted(name, tabSize, tabSize);
    }
}

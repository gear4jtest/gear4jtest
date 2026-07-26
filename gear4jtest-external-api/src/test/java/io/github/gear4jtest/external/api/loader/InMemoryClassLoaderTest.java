package io.github.gear4jtest.external.api.loader;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.github.gear4jtest.external.api.compiler.JavaxToolsGeneratedSourceCompiler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryClassLoaderTest {
    @Test
    void loadClass_shouldReleaseDefinedClassBytesButKeepConservativeWeight() throws Exception {
        // Given
        String className = "io.test.GeneratedForLoaderBudget";
        String source = """
                package io.test;
                public final class GeneratedForLoaderBudget {}
                """;
        Map<String, byte[]> compiled = new JavaxToolsGeneratedSourceCompiler(getClass().getClassLoader())
                .compile(className, source.getBytes(StandardCharsets.UTF_8));
        InMemoryClassLoader loader = new InMemoryClassLoader(getClass().getClassLoader());
        loader.addCompiledClasses(compiled);
        long compiledBytes = compiled.values().stream().mapToLong(bytes -> bytes.length).sum();

        // When
        Class<?> loaded = loader.loadClass(className);

        // Then
        assertThat(loaded.getName()).isEqualTo(className);
        assertThat(loader.retainedBytecodeBytes()).isZero();
        assertThat(loader.bytecodeWeightBytes()).isEqualTo(compiledBytes);
    }
}

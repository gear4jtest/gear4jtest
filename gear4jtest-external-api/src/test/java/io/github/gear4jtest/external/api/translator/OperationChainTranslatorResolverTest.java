package io.github.gear4jtest.external.api.translator;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationChainTranslatorResolverTest {
    @Test
    void resolve_shouldUseXmlAsDefaultMediaType() {
        OperationChainTranslator xmlTranslator = new StubTranslator("application/xml", false);
        OperationChainTranslator resolverTranslator = new OperationChainTranslatorResolver(List.of(xmlTranslator))
                .resolve(null);

        assertThat(resolverTranslator).isSameAs(xmlTranslator);
    }

    @Test
    void resolve_shouldSkipTranslatorsThrowingFromSupports() {
        OperationChainTranslator throwing = new StubTranslator("application/xml", true);
        OperationChainTranslator json = new StubTranslator("application/json", false);

        OperationChainTranslator resolved = new OperationChainTranslatorResolver(List.of(throwing, json))
                .resolve("application/json");

        assertThat(resolved).isSameAs(json);
    }

    @Test
    void resolve_shouldFailWhenNoTranslatorSupportsMediaType() {
        OperationChainTranslatorResolver resolver = new OperationChainTranslatorResolver(List.of(
                                                                                                 new StubTranslator(
                                                                                                         "application/xml",
                                                                                                         false)));

        assertThatThrownBy(() -> resolver.resolve("application/json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No OperationChainTranslator found for mediaType=application/json");
    }

    @Test
    void constructor_shouldDefensivelyCopyTranslators() {
        java.util.ArrayList<OperationChainTranslator> translators = new java.util.ArrayList<>();
        OperationChainTranslator xml = new StubTranslator("application/xml", false);
        translators.add(xml);
        OperationChainTranslatorResolver resolver = new OperationChainTranslatorResolver(translators);

        translators.clear();

        assertThat(resolver.resolve("application/xml")).isSameAs(xml);
    }

    private record StubTranslator(
                                  String supportedMediaType,
                                  boolean throwOnSupports)
            implements OperationChainTranslator {
        @Override
        public boolean supports(String mediaType) {
            if (throwOnSupports) {
                throw new IllegalStateException("boom");
            }
            return supportedMediaType.equals(mediaType);
        }

        @Override
        public OperationChainTranslator.GenerationResult translate(byte[] content, String mediaType) {
            return new OperationChainTranslator.GenerationResult("io.test.Generated",
                    "package io.test; public class Generated {}");
        }
    }
}

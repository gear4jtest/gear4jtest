package io.github.gear4jtest.external.api.translator;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationChainTranslatorResolverTest {
    @Test
    void resolve_shouldUseXmlAsDefaultMediaType() {
        OperationChainTranslator xmlTranslator = new StubTranslator("xml", "application/xml", false);
        OperationChainTranslator resolverTranslator = new OperationChainTranslatorResolver(List.of(xmlTranslator))
                .resolve(null);

        assertThat(resolverTranslator).isSameAs(xmlTranslator);
    }

    @Test
    void resolve_shouldReportTranslatorsThrowingFromSupports() {
        OperationChainTranslator throwing = new StubTranslator("broken", "application/xml", true);
        OperationChainTranslator json = new StubTranslator("json", "application/json", false);

        assertThatThrownBy(() -> new OperationChainTranslatorResolver(List.of(throwing, json))
                .resolve("application/json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("supports() failed for [broken]")
                .satisfies(exception -> assertThat(exception.getSuppressed()).hasSize(1));
    }

    @Test
    void resolve_shouldFailWhenNoTranslatorSupportsMediaType() {
        OperationChainTranslatorResolver resolver = new OperationChainTranslatorResolver(List.of(
                                                                                                 new StubTranslator(
                                                                                                         "xml",
                                                                                                         "application/xml",
                                                                                                         false)));

        assertThatThrownBy(() -> resolver.resolve("application/json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No OperationChainTranslator found for mediaType=application/json");
    }

    @Test
    void constructor_shouldDefensivelyCopyTranslators() {
        java.util.ArrayList<OperationChainTranslator> translators = new java.util.ArrayList<>();
        OperationChainTranslator xml = new StubTranslator("xml", "application/xml", false);
        translators.add(xml);
        OperationChainTranslatorResolver resolver = new OperationChainTranslatorResolver(translators);

        translators.clear();

        assertThat(resolver.resolve("application/xml")).isSameAs(xml);
    }

    @Test
    void resolve_shouldRejectAmbiguityAndAllowExplicitSelectionRegardlessOfOrder() {
        OperationChainTranslator alpha = new StubTranslator("alpha", "application/xml", false);
        OperationChainTranslator beta = new StubTranslator("beta", "application/xml", false);

        OperationChainTranslatorResolver forward = new OperationChainTranslatorResolver(List.of(alpha, beta));
        OperationChainTranslatorResolver reverse = new OperationChainTranslatorResolver(List.of(beta, alpha));

        assertThatThrownBy(() -> forward.resolve("application/xml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ambiguous OperationChainTranslator")
                .hasMessageContaining("[alpha, beta]");
        assertThatThrownBy(() -> reverse.resolve("application/xml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[alpha, beta]");
        assertThat(reverse.resolve("application/xml", "alpha")).isSameAs(alpha);
    }

    private record StubTranslator(
                                  String id,
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

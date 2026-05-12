package io.test.gear4jtest.external.api.translator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

public final class OperationChainTranslatorResolver {
    private final List<OperationChainTranslator> translators;

    /** Injection explicite (Spring/Guice/tests) */
    public OperationChainTranslatorResolver(List<OperationChainTranslator> translators) {
        this.translators = List.copyOf(Objects.requireNonNull(translators));
    }

    /** Découverte via ServiceLoader (plugins) */
    public static OperationChainTranslatorResolver fromServiceLoader(ClassLoader cl) {
        List<OperationChainTranslator> list = new ArrayList<>();
        ServiceLoader.load(OperationChainTranslator.class, cl).forEach(list::add);
        return new OperationChainTranslatorResolver(list);
    }

    public OperationChainTranslator resolve(String mediaType) {
        String mt = (mediaType == null || mediaType.isBlank()) ? "application/xml" : mediaType;
        return translators.stream().filter(c -> {
            try {
                return c.supports(mt);
            } catch (Throwable t) {
                return false;
            }
        }).findFirst()
                .orElseThrow(() -> new IllegalStateException("No OperationChainTranslator found for mediaType=" + mt));
    }
}

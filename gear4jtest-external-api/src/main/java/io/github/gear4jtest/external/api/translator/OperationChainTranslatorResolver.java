package io.github.gear4jtest.external.api.translator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;

public final class OperationChainTranslatorResolver {
    private final List<OperationChainTranslator> translators;

    /**
     * Creates a resolver from explicitly provided translators.
     */
    public OperationChainTranslatorResolver(List<OperationChainTranslator> translators) {
        this.translators = List.copyOf(Objects.requireNonNull(translators));
    }

    /**
     * Discovers translators through {@link java.util.ServiceLoader}.
     */
    public static OperationChainTranslatorResolver fromServiceLoader(ClassLoader cl) {
        List<OperationChainTranslator> list = new ArrayList<>();
        ServiceLoader.load(OperationChainTranslator.class, cl).forEach(list::add);
        return new OperationChainTranslatorResolver(list);
    }

    public OperationChainTranslator resolve(String mediaType) {
        return resolve(mediaType, null);
    }

    /**
     * Resolves a translator, optionally selecting one provider by its stable
     * {@link OperationChainTranslator#id() id}.
     */
    public OperationChainTranslator resolve(String mediaType, String translatorId) {
        String mt = (mediaType == null || mediaType.isBlank()) ? "application/xml" : mediaType;
        String requestedId = translatorId == null || translatorId.isBlank() ? null : translatorId.trim();
        List<OperationChainTranslator> candidates = translators.stream()
                .filter(translator -> requestedId == null || requestedId.equals(requiredId(translator)))
                .sorted(Comparator.comparing(OperationChainTranslatorResolver::requiredId))
                .toList();
        if (requestedId != null && candidates.isEmpty()) {
            throw new IllegalStateException("No OperationChainTranslator found with id=" + requestedId);
        }

        List<OperationChainTranslator> matches = new ArrayList<>();
        List<TranslatorProbeFailure> failures = new ArrayList<>();
        for (OperationChainTranslator candidate : candidates) {
            try {
                if (candidate.supports(mt)) {
                    matches.add(candidate);
                }
            } catch (RuntimeException exception) {
                failures.add(new TranslatorProbeFailure(requiredId(candidate), exception));
            }
        }
        if (!failures.isEmpty()) {
            failures.sort(Comparator.comparing(TranslatorProbeFailure::translatorId));
            IllegalStateException resolutionFailure = new IllegalStateException(
                    "Cannot resolve OperationChainTranslator for mediaType=" + mt + "; supports() failed for "
                            + failures.stream().map(TranslatorProbeFailure::translatorId).toList());
            failures.forEach(failure -> resolutionFailure.addSuppressed(failure.cause()));
            throw resolutionFailure;
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Ambiguous OperationChainTranslator for mediaType=" + mt + ": "
                    + matches.stream().map(OperationChainTranslatorResolver::requiredId).sorted().toList()
                    + ". Select one explicitly by id.");
        }
        if (matches.isEmpty()) {
            String selection = requestedId == null ? "" : " and id=" + requestedId;
            throw new IllegalStateException("No OperationChainTranslator found for mediaType=" + mt + selection);
        }
        return matches.get(0);
    }

    private static String requiredId(OperationChainTranslator translator) {
        String id = Objects.requireNonNull(translator.id(), "translator id must not be null").trim();
        if (id.isEmpty()) {
            throw new IllegalStateException("OperationChainTranslator id must not be blank: "
                    + translator.getClass().getName());
        }
        return id;
    }

    private record TranslatorProbeFailure(String translatorId, RuntimeException cause) {}
}

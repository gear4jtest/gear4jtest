package io.github.gear4jtest.xml.translator;

import io.github.gear4jtest.external.api.GeneratedCompilationConfiguration;

/**
 * Structural and generated-source limits applied to every XML translation.
 *
 * @param maxOperations           maximum total number of operations, including
 *                                nested operations
 * @param maxDependencies         maximum number of declared injected
 *                                dependencies
 * @param maxNestingDepth         maximum operation nesting depth, where a
 *                                top-level operation has depth one
 * @param maxGeneratedSourceBytes maximum UTF-8 size of raw and formatted
 *                                generated Java source
 */
public record XmlTranslationLimits(int maxOperations,
                                   int maxDependencies,
                                   int maxNestingDepth,
                                   long maxGeneratedSourceBytes) {

    public static final int DEFAULT_MAX_OPERATIONS = 1_000;
    public static final int DEFAULT_MAX_DEPENDENCIES = 256;
    public static final int DEFAULT_MAX_NESTING_DEPTH = 32;
    public static final long DEFAULT_MAX_GENERATED_SOURCE_BYTES = GeneratedCompilationConfiguration.DEFAULT_MAX_GENERATED_SOURCE_BYTES;

    public XmlTranslationLimits {
        if (maxOperations <= 0) {
            throw new IllegalArgumentException("maxOperations must be > 0");
        }
        if (maxDependencies <= 0) {
            throw new IllegalArgumentException("maxDependencies must be > 0");
        }
        if (maxNestingDepth <= 0) {
            throw new IllegalArgumentException("maxNestingDepth must be > 0");
        }
        if (maxGeneratedSourceBytes <= 0L) {
            throw new IllegalArgumentException("maxGeneratedSourceBytes must be > 0");
        }
    }

    public static XmlTranslationLimits defaults() {
        return new XmlTranslationLimits(DEFAULT_MAX_OPERATIONS, DEFAULT_MAX_DEPENDENCIES,
                DEFAULT_MAX_NESTING_DEPTH, DEFAULT_MAX_GENERATED_SOURCE_BYTES);
    }

    public XmlTranslationLimits withMaxOperations(int value) {
        return new XmlTranslationLimits(value, maxDependencies, maxNestingDepth, maxGeneratedSourceBytes);
    }

    public XmlTranslationLimits withMaxDependencies(int value) {
        return new XmlTranslationLimits(maxOperations, value, maxNestingDepth, maxGeneratedSourceBytes);
    }

    public XmlTranslationLimits withMaxNestingDepth(int value) {
        return new XmlTranslationLimits(maxOperations, maxDependencies, value, maxGeneratedSourceBytes);
    }

    public XmlTranslationLimits withMaxGeneratedSourceBytes(long value) {
        return new XmlTranslationLimits(maxOperations, maxDependencies, maxNestingDepth, value);
    }
}

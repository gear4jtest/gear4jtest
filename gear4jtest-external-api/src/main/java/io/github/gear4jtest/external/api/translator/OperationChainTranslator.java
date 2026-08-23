package io.github.gear4jtest.external.api.translator;

import java.util.Objects;

import io.github.gear4jtest.external.api.ExecutionMode;

public interface OperationChainTranslator {
    /**
     * Returns the stable identifier used for explicit SPI selection.
     *
     * <p>
     * Providers should override this when their implementation class name is not a
     * suitable configuration value.
     * </p>
     */
    default String id() {
        return getClass().getName();
    }

    /**
     * Returns true when this translator can handle the supplied media type.
     *
     * <p>
     * Examples: {@code application/xml}, {@code application/json},
     * {@code application/vnd.gear4j.assembly-line+xml}.
     * </p>
     */
    boolean supports(String mediaType);

    /**
     * Translates an external pipeline definition into Java source code.
     */
    GenerationResult translate(byte[] content, String mediaType) throws Exception;

    /**
     * Translates an external pipeline definition for a specific publication or
     * execution mode.
     *
     * <p>
     * Existing format translators remain compatible through the default
     * implementation. Translators with a mode-dependent capability surface should
     * override this method.
     * </p>
     */
    default GenerationResult translate(byte[] content, String mediaType, ExecutionMode mode) throws Exception {
        Objects.requireNonNull(mode, "mode must not be null");
        return translate(content, mediaType);
    }

    /**
     * @param className       fully-qualified generated class name
     * @param formattedSource formatted Java source code
     */
    record GenerationResult(String className, String formattedSource) {
        public GenerationResult {
            Objects.requireNonNull(className, "className");
            Objects.requireNonNull(formattedSource, "formattedSource");
            if (className.isBlank()) {
                throw new IllegalArgumentException("className must not be blank");
            }
            if (!className.contains(".")) {
                throw new IllegalArgumentException(
                        "className must be fully-qualified so the compiler and classloader resolve the same type: "
                                + className);
            }
        }
    }
}

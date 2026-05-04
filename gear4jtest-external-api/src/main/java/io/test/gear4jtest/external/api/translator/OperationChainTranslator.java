package io.test.gear4jtest.external.api.translator;

import java.util.Objects;

public interface OperationChainTranslator {

    /**
     * Returns true when this translator can handle the supplied media type.
     *
     * <p>Examples: {@code application/xml}, {@code application/json},
     * {@code application/vnd.gear4j.pipeline+xml}.</p>
     */
    boolean supports(String mediaType);

    /**
     * Translates an external pipeline definition into Java source code.
     */
    GenerationResult translate(byte[] content, String mediaType) throws Exception;

    /**
     * @param className fully-qualified generated class name
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

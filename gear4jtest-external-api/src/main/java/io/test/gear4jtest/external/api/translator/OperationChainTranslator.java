package io.test.gear4jtest.external.api.translator;

public interface OperationChainTranslator {

    /** ex: "application/xml", "application/json" */
    boolean supports(String mediaType);

    GenerationResult translate(byte[] content, String mediaType) throws Exception;

    record GenerationResult(String className, String formattedSource) { }
}
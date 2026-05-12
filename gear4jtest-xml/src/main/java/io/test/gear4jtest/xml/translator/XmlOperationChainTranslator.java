package io.test.gear4jtest.xml.translator;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Set;

import io.test.gear4jtest.external.api.translator.OperationChainTranslator;
import io.test.gear4jtest.xml.generator.XmlToJavaGenerator;
import io.test.gear4jtest.xml.model.XmlPipelineDefinition;
import io.test.gear4jtest.xml.parser.XmlPipelineParser;
import io.test.gear4jtest.xml.validator.AssemblyLineValidator;

public final class XmlOperationChainTranslator implements OperationChainTranslator {

    public static final String VENDOR_MEDIA_TYPE = "application/vnd.gear4j.pipeline+xml";

    private static final Set<String> SUPPORTED_MEDIA_TYPES = Set.of("application/xml", "text/xml", VENDOR_MEDIA_TYPE);

    private final AssemblyLineValidator validator;
    private final XmlPipelineParser parser;
    private final XmlToJavaGenerator generator;

    public XmlOperationChainTranslator() {
        this(new AssemblyLineValidator(), new XmlPipelineParser(), new XmlToJavaGenerator());
    }

    public XmlOperationChainTranslator(AssemblyLineValidator validator,
                                       XmlPipelineParser parser,
                                       XmlToJavaGenerator generator) {
        this.validator = validator;
        this.parser = parser;
        this.generator = generator;
    }

    @Override
    public boolean supports(String mediaType) {
        if (mediaType == null || mediaType.isBlank()) {
            return true;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        int semicolon = normalized.indexOf(';');
        if (semicolon >= 0) {
            normalized = normalized.substring(0, semicolon).trim();
        }
        return SUPPORTED_MEDIA_TYPES.contains(normalized) || normalized.endsWith("+xml");
    }

    @Override
    public GenerationResult translate(byte[] content, String mediaType) {
        if (!supports(mediaType)) {
            throw new IllegalArgumentException("Unsupported XML pipeline media type: " + mediaType);
        }
        validator.validate(content);
        XmlPipelineDefinition definition = parser.parse(new ByteArrayInputStream(content));
        return generator.generate(definition);
    }
}

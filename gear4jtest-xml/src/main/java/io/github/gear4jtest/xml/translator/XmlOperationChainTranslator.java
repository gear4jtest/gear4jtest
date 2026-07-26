package io.github.gear4jtest.xml.translator;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Set;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.xml.capability.XmlOperatorCapabilityPolicy;
import io.github.gear4jtest.xml.generator.XmlToJavaGenerator;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;
import io.github.gear4jtest.xml.parser.XmlAssemblyLineParser;
import io.github.gear4jtest.xml.validator.AssemblyLineValidator;

public final class XmlOperationChainTranslator implements OperationChainTranslator {
    public static final String VENDOR_MEDIA_TYPE = "application/vnd.gear4j.assembly-line+xml";
    private static final Set<String> SUPPORTED_MEDIA_TYPES = Set.of("application/xml", "text/xml", VENDOR_MEDIA_TYPE);
    private final AssemblyLineValidator validator;
    private final XmlAssemblyLineParser parser;
    private final XmlToJavaGenerator generator;

    public XmlOperationChainTranslator() {
        this(new AssemblyLineValidator(), new XmlAssemblyLineParser(), XmlToJavaGenerator.untrusted());
    }

    public static XmlOperationChainTranslator trusted() {
        return new XmlOperationChainTranslator(new AssemblyLineValidator(), new XmlAssemblyLineParser(),
                XmlToJavaGenerator.trusted());
    }

    public static XmlOperationChainTranslator gelOnly() {
        return new XmlOperationChainTranslator(new AssemblyLineValidator(), new XmlAssemblyLineParser(),
                XmlToJavaGenerator.gelOnly());
    }

    public static XmlOperationChainTranslator gelOnly(XmlOperatorCapabilityPolicy operatorCapabilityPolicy) {
        return new XmlOperationChainTranslator(new AssemblyLineValidator(), new XmlAssemblyLineParser(),
                XmlToJavaGenerator.gelOnly(operatorCapabilityPolicy));
    }

    public XmlOperationChainTranslator(AssemblyLineValidator validator,
                                       XmlAssemblyLineParser parser,
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
        return translate(content, mediaType, ExecutionMode.RUN);
    }

    @Override
    public GenerationResult translate(byte[] content, String mediaType, ExecutionMode mode) {
        if (!supports(mediaType)) {
            throw new IllegalArgumentException("Unsupported XML assembly-line media type: " + mediaType);
        }
        validator.validate(content);
        XmlAssemblyLineDefinition definition = parser.parse(new ByteArrayInputStream(content));
        return generator.generate(definition, mode);
    }
}

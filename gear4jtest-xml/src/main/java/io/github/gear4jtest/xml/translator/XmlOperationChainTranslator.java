package io.github.gear4jtest.xml.translator;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.xml.capability.XmlOperatorCapabilityPolicy;
import io.github.gear4jtest.xml.generator.XmlJavaSourcePolicy;
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
        this(XmlOperatorCapabilityPolicy.denyAll(), XmlTranslationLimits.defaults(), false);
    }

    public static XmlOperationChainTranslator trusted() {
        return trusted(XmlTranslationLimits.defaults());
    }

    public static XmlOperationChainTranslator trusted(XmlTranslationLimits limits) {
        return new XmlOperationChainTranslator(XmlOperatorCapabilityPolicy.trustedClassNames(), limits, true);
    }

    public static XmlOperationChainTranslator gelOnly() {
        return gelOnly(XmlOperatorCapabilityPolicy.denyAll(), XmlTranslationLimits.defaults());
    }

    public static XmlOperationChainTranslator gelOnly(XmlOperatorCapabilityPolicy operatorCapabilityPolicy) {
        return gelOnly(operatorCapabilityPolicy, XmlTranslationLimits.defaults());
    }

    public static XmlOperationChainTranslator gelOnly(XmlOperatorCapabilityPolicy operatorCapabilityPolicy,
                                                      XmlTranslationLimits limits) {
        return new XmlOperationChainTranslator(operatorCapabilityPolicy, limits, false);
    }

    private XmlOperationChainTranslator(XmlOperatorCapabilityPolicy operatorCapabilityPolicy,
                                        XmlTranslationLimits limits,
                                        boolean trusted) {
        XmlTranslationLimits effectiveLimits = Objects.requireNonNull(limits, "limits must not be null");
        this.validator = new AssemblyLineValidator();
        this.parser = new XmlAssemblyLineParser(XmlAssemblyLineParser.DEFAULT_MAX_XML_BYTES, effectiveLimits);
        XmlToJavaGenerator.Builder generatorBuilder = XmlToJavaGenerator.builder().translationLimits(effectiveLimits);
        if (trusted) {
            generatorBuilder.sourcePolicy(XmlJavaSourcePolicy.trusted());
        }
        this.generator = generatorBuilder
                .operatorCapabilityPolicy(Objects.requireNonNull(operatorCapabilityPolicy,
                                                                 "operatorCapabilityPolicy must not be null"))
                .build();
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

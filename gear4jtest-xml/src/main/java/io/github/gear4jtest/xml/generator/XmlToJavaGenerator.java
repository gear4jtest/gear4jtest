package io.github.gear4jtest.xml.generator;

import java.util.Objects;

import io.github.gear4jtest.external.api.ExecutionMode;
import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.xml.capability.XmlOperatorCapabilityPolicy;
import io.github.gear4jtest.xml.limit.XmlDefinitionBudget;
import io.github.gear4jtest.xml.model.XmlAssemblyLineDefinition;
import io.github.gear4jtest.xml.translator.XmlTranslationLimits;

/**
 * Public XML-to-Java generator facade.
 *
 * <p>
 * This class intentionally keeps only API/configuration concerns. The actual
 * generated-class rendering is delegated to small package-private renderers so
 * XML validation, support-method rendering and operation-method rendering can
 * evolve independently.
 * </p>
 */
public final class XmlToJavaGenerator {
    public static final String DEFAULT_PACKAGE = "io.github.gear4jtest.xml.generated";
    private final String packageName;
    private final ClassLoader classLoader;
    private final JavaSourceFormatter formatter;
    private final XmlJavaSourcePolicy sourcePolicy;
    private final XmlOperatorCapabilityPolicy operatorCapabilityPolicy;
    private final XmlTranslationLimits translationLimits;

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(String packageName) {
        return builder().packageName(packageName);
    }

    public static XmlToJavaGenerator trusted() {
        return trusted(DEFAULT_PACKAGE);
    }

    public static XmlToJavaGenerator trusted(String packageName) {
        return trusted(packageName, contextClassLoader(), JdtFormatter.defaultFormatter());
    }

    public static XmlToJavaGenerator trusted(String packageName,
                                             ClassLoader classLoader,
                                             JavaSourceFormatter formatter) {
        return builder(packageName)
                .classLoader(classLoader)
                .formatter(formatter)
                .sourcePolicy(XmlJavaSourcePolicy.trusted())
                .operatorCapabilityPolicy(XmlOperatorCapabilityPolicy.trustedClassNames())
                .build();
    }

    public static XmlToJavaGenerator untrusted() {
        return builder().build();
    }

    public static XmlToJavaGenerator untrusted(XmlOperatorCapabilityPolicy operatorCapabilityPolicy) {
        return builder().operatorCapabilityPolicy(operatorCapabilityPolicy).build();
    }

    public static XmlToJavaGenerator gelOnly() {
        return untrusted();
    }

    public static XmlToJavaGenerator gelOnly(XmlOperatorCapabilityPolicy operatorCapabilityPolicy) {
        return untrusted(operatorCapabilityPolicy);
    }

    private XmlToJavaGenerator(Builder builder) {
        this.sourcePolicy = XmlJavaSourcePolicy.require(builder.sourcePolicy);
        this.operatorCapabilityPolicy = Objects.requireNonNull(builder.operatorCapabilityPolicy,
                                                               "operatorCapabilityPolicy must not be null");
        this.translationLimits = Objects.requireNonNull(builder.translationLimits,
                                                        "translationLimits must not be null");
        this.sourcePolicy.validatePackageName(builder.packageName);
        this.packageName = Objects.requireNonNull(builder.packageName, "packageName");
        this.classLoader = builder.classLoader != null ? builder.classLoader : contextClassLoader();
        this.formatter = Objects.requireNonNull(builder.formatter, "formatter must not be null");
    }

    public static final class Builder {
        private String packageName = DEFAULT_PACKAGE;
        private ClassLoader classLoader = contextClassLoader();
        private JavaSourceFormatter formatter = JdtFormatter.defaultFormatter();
        private XmlJavaSourcePolicy sourcePolicy = XmlJavaSourcePolicy.forbidInlineJava();
        private XmlOperatorCapabilityPolicy operatorCapabilityPolicy = XmlOperatorCapabilityPolicy.denyAll();
        private XmlTranslationLimits translationLimits = XmlTranslationLimits.defaults();

        private Builder() {
        }

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder classLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        public Builder formatter(JavaSourceFormatter formatter) {
            this.formatter = formatter;
            return this;
        }

        public Builder sourcePolicy(XmlJavaSourcePolicy sourcePolicy) {
            this.sourcePolicy = sourcePolicy;
            return this;
        }

        public Builder operatorCapabilityPolicy(XmlOperatorCapabilityPolicy operatorCapabilityPolicy) {
            this.operatorCapabilityPolicy = operatorCapabilityPolicy;
            return this;
        }

        public Builder translationLimits(XmlTranslationLimits translationLimits) {
            this.translationLimits = translationLimits;
            return this;
        }

        public XmlToJavaGenerator build() {
            return new XmlToJavaGenerator(this);
        }
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }

    public OperationChainTranslator.GenerationResult generate(XmlAssemblyLineDefinition definition) {
        return generate(definition, ExecutionMode.RUN);
    }

    public OperationChainTranslator.GenerationResult generate(XmlAssemblyLineDefinition definition,
                                                              ExecutionMode mode) {
        XmlDefinitionBudget.validateDefinition(definition, translationLimits);
        XmlAssemblyLineDefinition resolvedDefinition = XmlOperatorCapabilityResolver.resolve(definition,
                                                                                             operatorCapabilityPolicy,
                                                                                             mode);
        XmlDefinitionSemanticValidator.validate(resolvedDefinition);
        GeneratedJavaSource generatedSource = new XmlGeneratedAssemblyLineRenderer(packageName, classLoader,
                sourcePolicy)
                .render(resolvedDefinition);
        XmlDefinitionBudget budget = new XmlDefinitionBudget(translationLimits);
        budget.requireGeneratedSource(generatedSource.source(), "Generated XML Java source");
        String formattedSource = formatter.format(generatedSource.source());
        budget.requireGeneratedSource(formattedSource, "Formatted XML Java source");
        return new OperationChainTranslator.GenerationResult(
                generatedSource.fullyQualifiedClassName(), formattedSource);
    }
}

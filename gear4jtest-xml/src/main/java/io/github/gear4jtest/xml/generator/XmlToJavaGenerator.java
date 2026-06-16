package io.github.gear4jtest.xml.generator;

import java.util.Objects;

import io.github.gear4jtest.external.api.translator.OperationChainTranslator;
import io.github.gear4jtest.xml.model.XmlPipelineDefinition;

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

    public XmlToJavaGenerator() {
        this(DEFAULT_PACKAGE);
    }

    public XmlToJavaGenerator(String packageName) {
        this(packageName, contextClassLoader());
    }

    public XmlToJavaGenerator(String packageName, ClassLoader classLoader) {
        this(packageName, classLoader, JdtFormatter.defaultFormatter());
    }

    public XmlToJavaGenerator(String packageName, ClassLoader classLoader, JavaSourceFormatter formatter) {
        this(packageName, classLoader, formatter, XmlJavaSourcePolicy.forbidInlineJava());
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
        return new XmlToJavaGenerator(packageName, classLoader, formatter, XmlJavaSourcePolicy.trusted());
    }

    public static XmlToJavaGenerator untrusted() {
        return new XmlToJavaGenerator();
    }

    public static XmlToJavaGenerator gelOnly() {
        return untrusted();
    }

    public XmlToJavaGenerator(String packageName,
                              ClassLoader classLoader,
                              JavaSourceFormatter formatter,
                              XmlJavaSourcePolicy sourcePolicy) {
        this.sourcePolicy = XmlJavaSourcePolicy.require(sourcePolicy);
        this.sourcePolicy.validatePackageName(packageName);
        this.packageName = Objects.requireNonNull(packageName, "packageName");
        this.classLoader = classLoader != null ? classLoader : contextClassLoader();
        this.formatter = Objects.requireNonNull(formatter, "formatter must not be null");
    }

    private static ClassLoader contextClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
    }

    public OperationChainTranslator.GenerationResult generate(XmlPipelineDefinition definition) {
        GeneratedJavaSource generatedSource = new XmlGeneratedPipelineRenderer(packageName, classLoader, sourcePolicy)
                .render(definition);
        return new OperationChainTranslator.GenerationResult(
                generatedSource.fullyQualifiedClassName(), formatter.format(generatedSource.source()));
    }
}

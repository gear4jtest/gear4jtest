package io.test.gear4test.xml.generator;

import java.io.File;
import java.io.InputStream;
import java.util.Objects;

import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.TypeSpec;
import io.github.gear4jtest.core.model.ElementModelBuilders;
import io.test.gear4jtest.xml.generated.AssemblyLine;
import io.test.gear4test.xml.visitor.JavaFlatCodeGeneratorVisitor;
import io.test.gear4test.xml.visitor.VisitorContext;
import io.test.gear4test.xml.visitor.XmlToJavaVisitor;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

/**
 * Générateur simplifié utilisant le pattern Visitor pour convertir des définitions XML en code Java.
 * Compatible Java 17.
 */
public class XmlToJavaGeneratorV4 {
    
    private final String packageName;
    private final XmlToJavaVisitor visitor;
    
    public XmlToJavaGeneratorV4(String packageName) {
        this.packageName = Objects.requireNonNull(packageName, "Package name cannot be null");
        this.visitor = new JavaFlatCodeGeneratorVisitor();
    }
    
    /**
     * Génère le fichier Java à partir d'une AssemblyLine XML.
     * Tout le travail est délégué au visitor.
     */
    public GenerationResult generateFromAssemblyLine(File xmlPath) throws Exception {
        AssemblyLine assemblyLine = unmarshal(xmlPath.toURI().toURL().openStream(), AssemblyLine.class);

        TypeSpec typeSpec = visitor.visit(assemblyLine, new VisitorContext());
        
        var javaFile = JavaFile.builder(packageName, typeSpec)
                .addStaticImport(ElementModelBuilders.class, "*")
                .skipJavaLangImports(true)
                .addFileComment("Généré automatiquement par XmlToJavaGeneratorV4")
                .build();

        String raw = javaFile.toString();
        String formatted = JdtFormatter.format(raw);

        return new GenerationResult(javaFile.typeSpec().name(), formatted);
    }
    
    /**
     * Désérialise un flux XML vers un objet JAXB.
     */
    public static <T> T unmarshal(InputStream source, Class<T> type) throws JAXBException {
        Objects.requireNonNull(source, "Source cannot be null");
        Objects.requireNonNull(type, "Type cannot be null");
        
        JAXBContext jaxbContext = JAXBContext.newInstance(type);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return type.cast(unmarshaller.unmarshal(source));
    }

    public record GenerationResult(String className, String formattedSource) {

    }
}
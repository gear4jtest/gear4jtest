package io.test.gear4test.xml.generator;

import java.io.File;
import java.io.InputStream;
import java.util.Objects;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import io.github.gear4jtest.core.model.ElementModelBuilders;
import io.test.gear4jtest.xml.generated.AssemblyLine;
import io.test.gear4test.xml.visitor.JavaCodeGeneratorVisitor;
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
    private final String className;
    private final XmlToJavaVisitor visitor;
    
    public XmlToJavaGeneratorV4(String packageName, String className) {
        this.packageName = Objects.requireNonNull(packageName, "Package name cannot be null");
        this.className = Objects.requireNonNull(className, "Class name cannot be null");
        this.visitor = new JavaCodeGeneratorVisitor();
    }
    
    /**
     * Génère le fichier Java à partir d'une AssemblyLine XML.
     * Tout le travail est délégué au visitor.
     */
    public JavaFile generateFromAssemblyLine(File xmlPath) throws Exception {
        AssemblyLine assemblyLine = unmarshal(xmlPath.toURI().toURL().openStream(), AssemblyLine.class);

        TypeSpec typeSpec = visitor.visit(assemblyLine, className, xmlPath.getName(), new VisitorContext());
        
        return JavaFile.builder(packageName, typeSpec)
                .addStaticImport(ElementModelBuilders.class, "*")
                .indent("    ")
                .addFileComment("Généré automatiquement par XmlToJavaGeneratorV4")
                .build();
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
}
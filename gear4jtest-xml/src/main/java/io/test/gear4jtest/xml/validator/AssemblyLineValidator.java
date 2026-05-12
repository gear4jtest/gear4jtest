package io.test.gear4jtest.xml.validator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

public final class AssemblyLineValidator {
    private static final String DEFAULT_SCHEMA = "/assembly-line.xsd";
    private final Schema schema;

    public AssemblyLineValidator() {
        this(DEFAULT_SCHEMA);
    }

    public AssemblyLineValidator(String schemaResourcePath) {
        this.schema = loadSchema(schemaResourcePath);
    }

    private static Schema loadSchema(String schemaResourcePath) {
        try (InputStream schemaStream = AssemblyLineValidator.class.getResourceAsStream(schemaResourcePath)) {
            if (schemaStream == null) {
                throw new IllegalArgumentException("Schema resource not found: " + schemaResourcePath);
            }
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newSchema(new StreamSource(schemaStream));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load XML schema: " + schemaResourcePath, e);
        }
    }

    public void validate(byte[] xml) {
        Objects.requireNonNull(xml, "xml");
        validate(new ByteArrayInputStream(xml));
    }

    public void validate(InputStream xml) {
        Objects.requireNonNull(xml, "xml");
        try {
            schema.newValidator().validate(new StreamSource(xml));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Gear4J XML pipeline definition", e);
        }
    }
}

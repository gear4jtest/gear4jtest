package io.github.gear4jtest.xml.validator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

public final class AssemblyLineValidator {
    public static final long DEFAULT_MAX_XML_BYTES = 2L * 1024L * 1024L;

    private static final String DEFAULT_SCHEMA = "/assembly-line.xsd";
    private final Schema schema;
    private final long maxXmlBytes;

    public AssemblyLineValidator() {
        this(DEFAULT_SCHEMA, DEFAULT_MAX_XML_BYTES);
    }

    public AssemblyLineValidator(long maxXmlBytes) {
        this(DEFAULT_SCHEMA, maxXmlBytes);
    }

    public AssemblyLineValidator(String schemaResourcePath) {
        this(schemaResourcePath, DEFAULT_MAX_XML_BYTES);
    }

    public AssemblyLineValidator(String schemaResourcePath, long maxXmlBytes) {
        if (maxXmlBytes <= 0) {
            throw new IllegalArgumentException("maxXmlBytes must be > 0");
        }
        this.schema = loadSchema(schemaResourcePath);
        this.maxXmlBytes = maxXmlBytes;
    }

    private static Schema loadSchema(String schemaResourcePath) {
        try (InputStream schemaStream = AssemblyLineValidator.class.getResourceAsStream(schemaResourcePath)) {
            if (schemaStream == null) {
                throw new IllegalArgumentException("Schema resource not found: " + schemaResourcePath);
            }
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newSchema(new StreamSource(schemaStream));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load XML schema: " + schemaResourcePath, e);
        }
    }

    public void validate(byte[] xml) {
        Objects.requireNonNull(xml, "xml");
        requireAllowedSize(xml.length);
        validateBounded(xml);
    }

    public void validate(InputStream xml) {
        Objects.requireNonNull(xml, "xml");
        try {
            validateBounded(readBounded(xml));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid Gear4J XML pipeline definition", exception);
        }
    }

    private void validateBounded(byte[] xml) {
        try {
            var validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.validate(new StreamSource(new ByteArrayInputStream(xml)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid Gear4J XML pipeline definition", e);
        }
    }

    private byte[] readBounded(InputStream xml) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while (true) {
            int requested = (int) Math.min(buffer.length, maxXmlBytes - total + 1L);
            read = xml.read(buffer, 0, requested);
            if (read == -1) {
                break;
            }
            if (read == 0) {
                int singleByte = xml.read();
                if (singleByte == -1) {
                    break;
                }
                total++;
                requireAllowedSize(total);
                output.write(singleByte);
                continue;
            }
            total += read;
            requireAllowedSize(total);
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void requireAllowedSize(long sizeBytes) {
        if (sizeBytes > maxXmlBytes) {
            throw new IllegalArgumentException("Gear4J XML definition exceeds maxXmlBytes=" + maxXmlBytes);
        }
    }
}

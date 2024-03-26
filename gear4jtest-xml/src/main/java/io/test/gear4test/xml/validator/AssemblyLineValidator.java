package io.test.gear4test.xml.validator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.io.InputStream;

public class AssemblyLineValidator {
    private static final Logger LOGGER =  LogManager.getLogger( "AssemblyLineValidator" );
    private static final String XSD_FILE = "/sample-assembly-line.xsd";
    /**
     * Validate a given file resource with a given schema validation resource.

     * @param resourceToValidate       the resource to be validated
     */
    public static FileParserReport validate(InputStream resourceToValidate) {
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
            schemaFactory.newSchema(new StreamSource(AssemblyLineValidator.class.getResourceAsStream(XSD_FILE)))
                    .newValidator()
                    .validate(new StreamSource(resourceToValidate));
            return FileParserReport.onSuccess();
        } catch (SAXException | IOException e) {
            LOGGER.error("Error while validating file", e);
            return FileParserReport.onError(e);
        }
    }

    public static class FileParserReport {
        private Status status;
        private Throwable throwable;

        public FileParserReport(Status status) {
            this(status, null);
        }

        public FileParserReport(Status status, Throwable throwable) {
            this.status = status;
            this.throwable = throwable;
        }

        static FileParserReport onError(Throwable throwable) {
            return new FileParserReport(Status.FAILED, throwable);
        }

        static FileParserReport onSuccess() {
            return new FileParserReport(Status.SUCCEEDED);
        }

        public Status getStatus() {
            return status;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public enum Status {
            FAILED,
            SUCCEEDED;
        }
    }
}

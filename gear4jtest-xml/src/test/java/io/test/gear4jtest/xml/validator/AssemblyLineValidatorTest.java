package io.test.gear4jtest.xml.validator;

import io.test.gear4test.xml.validator.AssemblyLineValidator;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyLineValidatorTest {

//    @Test
//    void testXsdValidationSuccessful() {
//        // Given
//        InputStream io = getClass().getResourceAsStream("/samples/good-assembly-line.xml");
//
//        // When
//        AssemblyLineValidator.FileParserReport report = AssemblyLineValidator.validate(io);
//
//        // Then
//        assertThat(report)
//                .as("Should have be successfully validated")
//                .extracting(
//                        AssemblyLineValidator.FileParserReport::getStatus,
//                        AssemblyLineValidator.FileParserReport::getThrowable)
//                .containsExactly(AssemblyLineValidator.FileParserReport.Status.SUCCEEDED, null);
//    }

    @Test
    void testXsdValidationFailed() {
        // Given
        InputStream io = getClass().getResourceAsStream("/samples/bad-assembly-line.xml");

        // When
        AssemblyLineValidator.FileParserReport report = AssemblyLineValidator.validate(io);

        // Then
        assertThat(report.getStatus())
                .as("Validation should have failed")
                .isEqualTo(AssemblyLineValidator.FileParserReport.Status.FAILED);
        assertThat(report.getThrowable())
                .as("Throwable should have been filled")
                .isInstanceOf(SAXParseException.class);
    }
}

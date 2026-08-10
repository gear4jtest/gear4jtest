package io.github.gear4jtest.xml2java

import groovy.transform.PackageScope
import org.gradle.api.GradleException

/**
 * Reads one Gradle XML input without allocating beyond its configured byte budget.
 */
@PackageScope
final class BoundedXmlInput {
    private BoundedXmlInput() {
    }

    static byte[] read(File file, long maxXmlBytes) {
        file.withInputStream { InputStream input ->
            BoundedXmlInput.read(input, file.path, maxXmlBytes)
        }
    }

    static byte[] read(InputStream input, String sourceDescription, long maxXmlBytes) {
        if (maxXmlBytes <= 0L) {
            throw new GradleException('maxXmlBytes must be > 0')
        }
        if (maxXmlBytes >= Integer.MAX_VALUE) {
            throw new GradleException('maxXmlBytes must be < ' + Integer.MAX_VALUE)
        }
        byte[] xml = input.readNBytes(Math.toIntExact(maxXmlBytes + 1L))
        if (xml.length > maxXmlBytes) {
            throw new GradleException(
                "Gear4J XML definition exceeds maxXmlBytes=${maxXmlBytes}: ${sourceDescription}"
            )
        }
        return xml
    }
}

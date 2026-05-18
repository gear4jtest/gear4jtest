package io.test.gear4jtest.xml.generator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

public final class JdtFormatter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdtFormatter.class);

    private JdtFormatter() {
    }

    public static JavaSourceFormatter defaultFormatter() {
        return JdtFormatter::format;
    }

    public static JavaSourceFormatter fromEclipseProfile(Path profilePath, String profileName) {
        Objects.requireNonNull(profilePath, "profilePath must not be null");
        Map<String, String> options = loadEclipseProfile(profilePath, profileName);
        return source -> format(source, options);
    }

    public static String format(String src) {
        return format(src, defaultOptions());
    }

    public static String format(String src, Map<String, String> options) {
        CodeFormatter formatter = ToolFactory.createCodeFormatter(options);
        TextEdit edit = formatter.format(CodeFormatter.K_COMPILATION_UNIT, src, 0, src.length(), 0,
                                         System.lineSeparator());
        if (edit == null) {
            LOGGER.warn("JDT formatter returned no edit. Generated Java source will be left unformatted.");
            return src;
        }

        IDocument doc = new Document(src);
        try {
            edit.apply(doc);
            return doc.get();
        } catch (Exception e) {
            LOGGER.warn("Could not format generated Java source. Generated Java source will be left unformatted.", e);
            return src;
        }
    }

    private static Map<String, String> defaultOptions() {
        Map<String, String> opts = DefaultCodeFormatterConstants.getEclipseDefaultSettings();
        opts.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_17);
        opts.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
        opts.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
        opts.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, "140");
        opts.put(DefaultCodeFormatterConstants.FORMATTER_JOIN_WRAPPED_LINES, DefaultCodeFormatterConstants.FALSE);
        return opts;
    }

    private static Map<String, String> loadEclipseProfile(Path profilePath, String profileName) {
        Map<String, String> options = defaultOptions();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            var document = factory.newDocumentBuilder().parse(profilePath.toFile());
            var profiles = document.getElementsByTagName("profile");
            Element selected = null;
            for (int i = 0; i < profiles.getLength(); i++) {
                Element profile = (Element) profiles.item(i);
                if (profileName == null || profileName.isBlank() || profileName.equals(profile.getAttribute("name"))) {
                    selected = profile;
                    break;
                }
            }
            if (selected == null) {
                throw new IllegalArgumentException("Eclipse formatter profile not found: " + profileName);
            }
            var settings = selected.getElementsByTagName("setting");
            Map<String, String> loaded = new HashMap<>();
            for (int i = 0; i < settings.getLength(); i++) {
                Element setting = (Element) settings.item(i);
                loaded.put(setting.getAttribute("id"), setting.getAttribute("value"));
            }
            options.putAll(loaded);
            return options;
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read Eclipse formatter profile: " + profilePath, e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to load Eclipse formatter profile: " + profilePath, e);
        }
    }
}

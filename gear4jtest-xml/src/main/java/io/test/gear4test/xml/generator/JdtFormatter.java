package io.test.gear4test.xml.generator;

import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;

public final class JdtFormatter {
    private JdtFormatter() {
    }

    public static String format(String src) {
        Map<String, String> opts = DefaultCodeFormatterConstants.getEclipseDefaultSettings();
        // Réglages clés (adapte à ton style) :
        opts.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_17);
        opts.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
        opts.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
        opts.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, "140");
        opts.put(DefaultCodeFormatterConstants.FORMATTER_JOIN_WRAPPED_LINES, DefaultCodeFormatterConstants.FALSE);

        CodeFormatter cf = ToolFactory.createCodeFormatter(opts);
        TextEdit edit = cf.format(CodeFormatter.K_COMPILATION_UNIT, src, 0, src.length(), 0, System.lineSeparator());
        if (edit == null)
            return src; // en cas d’erreur, retourne brut

        IDocument doc = new Document(src);
        try {
            edit.apply(doc);
            return doc.get();
        } catch (Exception e) {
            return src;
        }
    }
}

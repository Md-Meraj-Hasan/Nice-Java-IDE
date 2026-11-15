package com.meraj.nicejavaide;

import android.graphics.Color;
import android.text.Editable;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight syntax coloring for HTML, CSS, JavaScript and common text formats. */
public final class CodeSyntaxHighlighter {
    private static final int TAG = Color.rgb(86, 156, 214);
    private static final int ATTRIBUTE = Color.rgb(156, 220, 254);
    private static final int STRING = Color.rgb(206, 145, 120);
    private static final int KEYWORD = Color.rgb(197, 134, 192);
    private static final int COMMENT = Color.rgb(106, 153, 85);
    private static final int NUMBER = Color.rgb(181, 206, 168);
    private CodeSyntaxHighlighter() {}

    public static void apply(Editable text, String extension) {
        if ("java".equals(extension)) {
            JavaSyntaxHighlighter.apply(text);
            return;
        }
        ForegroundColorSpan[] old = text.getSpans(0, text.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : old) text.removeSpan(span);
        String source = text.toString();
        if ("kt".equals(extension) || "kts".equals(extension)) {
            paint(text, source, "/\\*[\\s\\S]*?\\*/|(?m)//.*$", COMMENT);
            paint(text, source, "\"\"\"[\\s\\S]*?\"\"\"|\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'", STRING);
            paint(text, source, "@[A-Za-z_][\\w.]*", ATTRIBUTE);
            paint(text, source, "\\b(package|import|as|class|interface|object|enum|annotation|data|sealed|open|abstract|final|override|private|protected|public|internal|inline|infix|operator|suspend|tailrec|const|lateinit|vararg|noinline|crossinline|fun|val|var|typealias|constructor|init|this|super|return|break|continue|throw|try|catch|finally|if|else|when|for|while|do|is|!is|in|!in|as\\?|typeof|by|where|out|reified|companion|expect|actual|external|dynamic|true|false|null)\\b", KEYWORD);
            paint(text, source, "\\b\\d+(?:\\.\\d+)?[fFdDlLuU]*\\b", NUMBER);
        } else if ("html".equals(extension) || "htm".equals(extension) || "xml".equals(extension)) {
            paint(text, source, "<!--[\\s\\S]*?-->", COMMENT);
            paint(text, source, "</?[A-Za-z][^>]*?>", TAG);
            paint(text, source, "\\b[A-Za-z_:][-A-Za-z0-9_:.]*(?=\\s*=)", ATTRIBUTE);
            paint(text, source, "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'", STRING);
        } else if ("css".equals(extension)) {
            paint(text, source, "/\\*[\\s\\S]*?\\*/", COMMENT);
            paint(text, source, "(?m)//.*$", COMMENT);
            paint(text, source, "[.#]?[A-Za-z_][\\w-]*(?=\\s*[{:,])", TAG);
            paint(text, source, "--?[A-Za-z_][\\w-]*", ATTRIBUTE);
            paint(text, source, "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'", STRING);
            paint(text, source, "-?\\b\\d+(?:\\.\\d+)?(?:px|em|rem|%|vh|vw|s|ms)?\\b", NUMBER);
        } else if ("js".equals(extension)) {
            paint(text, source, "/\\*[\\s\\S]*?\\*/|(?m)//.*$", COMMENT);
            paint(text, source, "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|`[^`]*`", STRING);
            paint(text, source, "\\b(const|let|var|function|return|if|else|for|while|do|switch|case|break|continue|new|class|extends|this|super|try|catch|finally|throw|async|await|import|export|from|default|typeof|instanceof|in|of|true|false|null|undefined)\\b", KEYWORD);
            paint(text, source, "\\b\\d+(?:\\.\\d+)?\\b", NUMBER);
        } else if ("json".equals(extension)) {
            paint(text, source, "\"(?:[^\"\\\\]|\\\\.)*\"(?=\\s*:)", ATTRIBUTE);
            paint(text, source, "\"(?:[^\"\\\\]|\\\\.)*\"", STRING);
            paint(text, source, "\\b(true|false|null)\\b", KEYWORD);
            paint(text, source, "-?\\b\\d+(?:\\.\\d+)?\\b", NUMBER);
        } else if ("py".equals(extension)) {
            paint(text, source, "(?m)#.*$", COMMENT);
            paint(text, source, "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'", STRING);
            paint(text, source, "\\b(def|class|return|if|elif|else|for|while|in|import|from|as|try|except|finally|raise|with|lambda|pass|break|continue|and|or|not|True|False|None)\\b", KEYWORD);
            paint(text, source, "\\b\\d+(?:\\.\\d+)?\\b", NUMBER);
        } else if ("md".equals(extension)) {
            paint(text, source, "(?m)^#{1,6}.*$", TAG);
            paint(text, source, "`[^`]+`", STRING);
        }
    }

    private static void paint(Editable text, String source, String regex, int color) {
        Matcher matcher = Pattern.compile(regex).matcher(source);
        while (matcher.find()) {
            text.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}

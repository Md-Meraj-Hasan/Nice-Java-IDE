package com.meraj.nicejavaide;

import android.graphics.Color;
import android.text.Editable;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Lightweight offline Java lexer for editor coloring (not a compiler/parser). */
public final class JavaSyntaxHighlighter {
    private static final int KEYWORD = Color.rgb(197, 134, 192);
    private static final int STRING = Color.rgb(206, 145, 120);
    private static final int COMMENT = Color.rgb(106, 153, 85);
    private static final int NUMBER = Color.rgb(181, 206, 168);
    private static final int ANNOTATION = Color.rgb(78, 201, 176);

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "if", "implements", "import", "instanceof", "int", "interface",
            "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "try",
            "void", "volatile", "while", "var", "record", "sealed", "permits",
            "yield", "true", "false", "null"
    ));

    private JavaSyntaxHighlighter() {}

    public static void apply(Editable text) {
        ForegroundColorSpan[] old = text.getSpans(0, text.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : old) text.removeSpan(span);

        int i = 0;
        final int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                int end = i + 2;
                while (end < n && text.charAt(end) != '\n') end++;
                color(text, i, end, COMMENT);
                i = end;
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int end = i + 2;
                while (end + 1 < n && !(text.charAt(end) == '*' && text.charAt(end + 1) == '/')) end++;
                end = Math.min(n, end + (end + 1 < n ? 2 : 0));
                color(text, i, end, COMMENT);
                i = end;
            } else if (c == '"' || c == '\'') {
                char quote = c;
                int end = i + 1;
                boolean escaped = false;
                while (end < n) {
                    char q = text.charAt(end++);
                    if (escaped) escaped = false;
                    else if (q == '\\') escaped = true;
                    else if (q == quote) break;
                }
                color(text, i, end, STRING);
                i = end;
            } else if (c == '@' && i + 1 < n && Character.isJavaIdentifierStart(text.charAt(i + 1))) {
                int end = i + 2;
                while (end < n && Character.isJavaIdentifierPart(text.charAt(end))) end++;
                color(text, i, end, ANNOTATION);
                i = end;
            } else if (Character.isJavaIdentifierStart(c)) {
                int end = i + 1;
                while (end < n && Character.isJavaIdentifierPart(text.charAt(end))) end++;
                String word = text.subSequence(i, end).toString();
                if (KEYWORDS.contains(word)) color(text, i, end, KEYWORD);
                i = end;
            } else if (Character.isDigit(c)) {
                int end = i + 1;
                while (end < n) {
                    char d = text.charAt(end);
                    if (Character.isLetterOrDigit(d) || d == '.' || d == '_') end++;
                    else break;
                }
                color(text, i, end, NUMBER);
                i = end;
            } else {
                i++;
            }
        }
    }

    private static void color(Editable text, int start, int end, int color) {
        if (start < end) text.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}

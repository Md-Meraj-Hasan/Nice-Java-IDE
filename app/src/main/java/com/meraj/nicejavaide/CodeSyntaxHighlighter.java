package com.meraj.nicejavaide;

import android.graphics.Color;
import android.text.Editable;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Incremental, line-based syntax highlighter for the native editor.
 *
 * Only the edited line(s) are tokenized. Highlighting continues into following
 * lines only when a multiline lexical state (for example /* ... *&#47;) changes.
 * Existing spans on unchanged lines are retained.
 */
public final class CodeSyntaxHighlighter {
    private static final int TAG = Color.rgb(86, 156, 214);
    private static final int ATTRIBUTE = Color.rgb(156, 220, 254);
    private static final int STRING = Color.rgb(206, 145, 120);
    private static final int KEYWORD = Color.rgb(197, 134, 192);
    private static final int COMMENT = Color.rgb(106, 153, 85);
    private static final int NUMBER = Color.rgb(181, 206, 168);

    private static final Set<String> JAVA_KEYWORDS = set(
            "abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while var record sealed permits yield true false null");
    private static final Set<String> KOTLIN_KEYWORDS = set(
            "package import as class interface object enum annotation data sealed open abstract final override private protected public internal inline infix operator suspend tailrec const lateinit vararg noinline crossinline fun val var typealias constructor init this super return break continue throw try catch finally if else when for while do is in by where out reified companion expect actual external dynamic true false null");
    private static final Set<String> JS_KEYWORDS = set(
            "const let var function return if else for while do switch case break continue new class extends this super try catch finally throw async await import export from default typeof instanceof in of true false null undefined");
    private static final Set<String> PY_KEYWORDS = set(
            "def class return if elif else for while in import from as try except finally raise with lambda pass break continue and or not True False None");
    private static final Set<String> EMPTY_KEYWORDS = new HashSet<>();

    private static Set<String> set(String words) {
        return new HashSet<>(Arrays.asList(words.split("\\s+")));
    }

    private static final class State {
        boolean blockComment;
        boolean htmlComment;
        boolean tripleString;

        State copy() {
            State s = new State();
            s.blockComment = blockComment;
            s.htmlComment = htmlComment;
            s.tripleString = tripleString;
            return s;
        }

        boolean same(State other) {
            return other != null && blockComment == other.blockComment
                    && htmlComment == other.htmlComment
                    && tripleString == other.tripleString;
        }
    }

    private static final class LineInfo {
        final String text;
        final State in;
        final State out;

        LineInfo(String text, State in, State out) {
            this.text = text;
            this.in = in;
            this.out = out;
        }
    }

    private final List<LineInfo> cache = new ArrayList<>();
    private String cachedExtension = "";

    /** Full refresh for a newly loaded file or a language change. */
    public void resetAndHighlight(Editable text, String extension) {
        cache.clear();
        cachedExtension = normalize(extension);
        ForegroundColorSpan[] spans = text.getSpans(0, text.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : spans) text.removeSpan(span);
        highlightFrom(text, cachedExtension, 0, 0, 0, 0, 0, true);
    }

    /**
     * Incrementally refresh after a TextWatcher change using the pre-edit line location.
     */
    public void update(Editable text, String extension, int startLine, int startLineOffset,
                       int changedEndLine, int oldNewlines, int newNewlines) {
        String ext = normalize(extension);
        if (!ext.equals(cachedExtension) || cache.isEmpty()) {
            resetAndHighlight(text, ext);
            return;
        }
        highlightFrom(text, ext, startLine, startLineOffset,
                Math.max(startLine, changedEndLine), oldNewlines, newNewlines, false);
    }

    private static String normalize(String extension) {
        return extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    }

    private static int countNewlines(CharSequence value) {
        if (value == null) return 0;
        int count = 0;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == '\n') count++;
        return count;
    }

    private static int lineEnd(Editable text, int start) {
        int i = start;
        while (i < text.length() && text.charAt(i) != '\n') i++;
        return i;
    }

    private void highlightFrom(Editable text, String ext, int startLine, int startLineOffset,
                               int changedEndLine, int oldNewlines, int newNewlines, boolean full) {
        int lineDelta = newNewlines - oldNewlines;
        int totalLines = full ? countNewlines(text) + 1 : Math.max(1, cache.size() + lineDelta);
        startLine = Math.max(0, Math.min(startLine, totalLines - 1));
        State state = startLine > 0 && startLine - 1 < cache.size()
                ? cache.get(startLine - 1).out.copy() : new State();

        // Keep the unchanged cache tail in place. For ordinary edits (no line
        // insertion/deletion), replace only the changed LineInfo entries.
        List<LineInfo> updated = new ArrayList<>();
        int line = startLine;
        int start = Math.max(0, Math.min(startLineOffset, text.length()));
        boolean converged = false;
        int convergedOldLine = -1;

        while (line < totalLines) {
            int end = lineEnd(text, start);
            String lineText = text.subSequence(start, end).toString();

            int oldLine = full ? -1 : line - lineDelta;
            if (!full && line > changedEndLine && oldLine >= 0 && oldLine < cache.size()) {
                LineInfo old = cache.get(oldLine);
                if (old.text.equals(lineText) && old.in.same(state)) {
                    converged = true;
                    convergedOldLine = oldLine;
                    break;
                }
            }

            removeLineSpans(text, start, end);
            State incoming = state.copy();
            State outgoing = colorLine(text, start, end, ext, state);
            updated.add(new LineInfo(lineText, incoming, outgoing.copy()));
            state = outgoing;
            line++;
            start = end < text.length() ? end + 1 : end;
        }

        if (full) {
            cache.clear();
            cache.addAll(updated);
        } else if (lineDelta == 0) {
            for (int i = 0; i < updated.size(); i++) {
                int index = startLine + i;
                if (index < cache.size()) cache.set(index, updated.get(i));
                else cache.add(updated.get(i));
            }
        } else {
            int oldTailStart = converged ? convergedOldLine : cache.size();
            oldTailStart = Math.max(startLine, Math.min(oldTailStart, cache.size()));
            cache.subList(startLine, oldTailStart).clear();
            cache.addAll(startLine, updated);
        }
        if (cache.size() > totalLines) cache.subList(totalLines, cache.size()).clear();
    }

    private static void removeLineSpans(Editable text, int start, int end) {
        int queryEnd = end > start ? end : Math.min(text.length(), start + 1);
        if (queryEnd <= start) return;
        ForegroundColorSpan[] spans = text.getSpans(start, queryEnd, ForegroundColorSpan.class);
        for (ForegroundColorSpan span : spans) {
            int spanStart = text.getSpanStart(span);
            int spanEnd = text.getSpanEnd(span);
            if (spanStart < end && spanEnd > start) text.removeSpan(span);
        }
    }

    private static State colorLine(Editable text, int start, int end, String ext, State state) {
        int i = start;
        while (i < end) {
            char c = text.charAt(i);
            char next = i + 1 < end ? text.charAt(i + 1) : '\0';

            if (state.htmlComment) {
                int close = indexOf(text, "-->", i, end);
                int stop = close < 0 ? end : close + 3;
                color(text, i, stop, COMMENT);
                i = stop;
                if (close >= 0) state.htmlComment = false;
                continue;
            }
            if (state.blockComment) {
                int close = indexOf(text, "*/", i, end);
                int stop = close < 0 ? end : close + 2;
                color(text, i, stop, COMMENT);
                i = stop;
                if (close >= 0) state.blockComment = false;
                continue;
            }
            if (state.tripleString) {
                int close = indexOf(text, "\"\"\"", i, end);
                int stop = close < 0 ? end : close + 3;
                color(text, i, stop, STRING);
                i = stop;
                if (close >= 0) state.tripleString = false;
                continue;
            }

            if (("html".equals(ext) || "htm".equals(ext) || "xml".equals(ext)) && c == '<' && i + 3 < end
                    && text.charAt(i + 1) == '!' && text.charAt(i + 2) == '-' && text.charAt(i + 3) == '-') {
                int close = indexOf(text, "-->", i + 4, end);
                int stop = close < 0 ? end : close + 3;
                color(text, i, stop, COMMENT);
                state.htmlComment = close < 0;
                i = stop;
                continue;
            }
            if (c == '/' && next == '*' && isBlockCommentLanguage(ext)) {
                int close = indexOf(text, "*/", i + 2, end);
                int stop = close < 0 ? end : close + 2;
                color(text, i, stop, COMMENT);
                state.blockComment = close < 0;
                i = stop;
                continue;
            }
            if (((c == '/' && next == '/') && isSlashCommentLanguage(ext))
                    || ("py".equals(ext) && c == '#')) {
                color(text, i, end, COMMENT);
                break;
            }

            if (("kt".equals(ext) || "kts".equals(ext) || "java".equals(ext)) && i + 2 < end
                    && c == '"' && text.charAt(i + 1) == '"' && text.charAt(i + 2) == '"') {
                int close = indexOf(text, "\"\"\"", i + 3, end);
                int stop = close < 0 ? end : close + 3;
                color(text, i, stop, STRING);
                state.tripleString = close < 0;
                i = stop;
                continue;
            }

            if (c == '"' || c == '\'' || (("js".equals(ext)) && c == '`')) {
                char quote = c;
                int stop = i + 1;
                boolean escaped = false;
                while (stop < end) {
                    char q = text.charAt(stop++);
                    if (escaped) escaped = false;
                    else if (q == '\\') escaped = true;
                    else if (q == quote) break;
                }
                color(text, i, stop, STRING);
                i = stop;
                continue;
            }

            if (c == '@' && i + 1 < end && Character.isJavaIdentifierStart(text.charAt(i + 1))) {
                int stop = i + 2;
                while (stop < end && (Character.isJavaIdentifierPart(text.charAt(stop)) || text.charAt(stop) == '.')) stop++;
                color(text, i, stop, ATTRIBUTE);
                i = stop;
                continue;
            }

            if (Character.isJavaIdentifierStart(c)) {
                int stop = i + 1;
                while (stop < end && Character.isJavaIdentifierPart(text.charAt(stop))) stop++;
                String word = text.subSequence(i, stop).toString();
                Set<String> words = keywordSet(ext);
                if (words.contains(word)) color(text, i, stop, KEYWORD);
                else if (("html".equals(ext) || "htm".equals(ext) || "xml".equals(ext))
                        && isInsideTag(text, start, i)) color(text, i, stop, ATTRIBUTE);
                i = stop;
                continue;
            }

            if (Character.isDigit(c)) {
                int stop = i + 1;
                while (stop < end) {
                    char d = text.charAt(stop);
                    if (Character.isLetterOrDigit(d) || d == '.' || d == '_') stop++;
                    else break;
                }
                color(text, i, stop, NUMBER);
                i = stop;
                continue;
            }

            if (("css".equals(ext)) && (c == '.' || c == '#') && i + 1 < end
                    && Character.isLetter(text.charAt(i + 1))) {
                int stop = i + 2;
                while (stop < end && (Character.isLetterOrDigit(text.charAt(stop)) || text.charAt(stop) == '-' || text.charAt(stop) == '_')) stop++;
                color(text, i, stop, TAG);
                i = stop;
                continue;
            }
            i++;
        }

        if ("html".equals(ext) || "htm".equals(ext) || "xml".equals(ext)) {
            // Color the tag delimiters; token spans above color tag names/attributes.
            for (int p = start; p < end; p++) {
                if (text.charAt(p) == '<' || text.charAt(p) == '>') color(text, p, p + 1, TAG);
            }
        } else if ("md".equals(ext) && start < end && text.charAt(start) == '#') {
            color(text, start, end, TAG);
        }
        return state;
    }

    private static boolean isBlockCommentLanguage(String ext) {
        return "java".equals(ext) || "kt".equals(ext) || "kts".equals(ext)
                || "js".equals(ext) || "css".equals(ext);
    }

    private static boolean isSlashCommentLanguage(String ext) {
        return "java".equals(ext) || "kt".equals(ext) || "kts".equals(ext)
                || "js".equals(ext) || "css".equals(ext);
    }

    private static Set<String> keywordSet(String ext) {
        if ("java".equals(ext)) return JAVA_KEYWORDS;
        if ("kt".equals(ext) || "kts".equals(ext)) return KOTLIN_KEYWORDS;
        if ("js".equals(ext)) return JS_KEYWORDS;
        if ("py".equals(ext)) return PY_KEYWORDS;
        return EMPTY_KEYWORDS;
    }

    private static boolean isInsideTag(Editable text, int lineStart, int offset) {
        int open = -1, close = -1;
        for (int i = lineStart; i < offset; i++) {
            if (text.charAt(i) == '<') open = i;
            else if (text.charAt(i) == '>') close = i;
        }
        return open > close;
    }

    private static int indexOf(Editable text, String value, int from, int end) {
        int max = end - value.length();
        for (int i = from; i <= max; i++) {
            boolean match = true;
            for (int j = 0; j < value.length(); j++) {
                if (text.charAt(i + j) != value.charAt(j)) { match = false; break; }
            }
            if (match) return i;
        }
        return -1;
    }

    private static void color(Editable text, int start, int end, int color) {
        if (start < end) text.setSpan(new ForegroundColorSpan(color), start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

}

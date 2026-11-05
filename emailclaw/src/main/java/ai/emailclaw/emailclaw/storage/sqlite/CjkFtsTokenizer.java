/*
 * The MIT License (MIT)
 * Copyright © 2026 the original author or authors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ai.emailclaw.emailclaw.storage.sqlite;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Hybrid CJK (Chinese/Japanese/Korean) and Latin tokenizer for SQLite FTS5 full-text search.
 *
 * <p>Standard SQLite FTS5 {@code unicode61} treats contiguous CJK characters as a single monolithic
 * token, causing word-level and phrase-level recall failures in Chinese, Japanese, and Korean text.
 * This tokenizer provides zero-dependency, deterministic token normalization:
 * <ul>
 *   <li><b>Index-time transformation</b>: Contiguous CJK runs are expanded into overlapping
 *       character bigrams plus single-character unigrams, while Latin/numeric tokens are preserved
 *       for standard {@code unicode61} indexing.</li>
 *   <li><b>Query-time transformation</b>: Contiguous CJK runs of length &ge; 2 are rewritten into
 *       conjunctive bigram clauses, single CJK characters match unigram tokens, and Latin tokens
 *       use prefix/term matching.</li>
 * </ul>
 */
public final class CjkFtsTokenizer {

    private CjkFtsTokenizer() {}

    /**
     * Transforms raw document text into an FTS5-indexable space-delimited token stream.
     *
     * @param raw raw document text to index
     * @return space-delimited token stream suitable for FTS5 {@code unicode61}
     */
    public static String tokenizeForIndex(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() * 2);
        StringBuilder cjkBuffer = new StringBuilder();

        int length = raw.length();
        for (int i = 0; i < length; ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);

            if (isCjkCodePoint(cp)) {
                cjkBuffer.appendCodePoint(cp);
            } else {
                if (!cjkBuffer.isEmpty()) {
                    emitCjkIndexTokens(cjkBuffer.toString(), out);
                    cjkBuffer.setLength(0);
                }
                if (Character.isLetterOrDigit(cp) || cp == '_' || cp == '-' || cp == '.') {
                    out.appendCodePoint(Character.toLowerCase(cp));
                } else if (!out.isEmpty() && out.charAt(out.length() - 1) != ' ') {
                    out.append(' ');
                }
            }
        }
        if (!cjkBuffer.isEmpty()) {
            emitCjkIndexTokens(cjkBuffer.toString(), out);
        }
        return out.toString().trim();
    }

    /**
     * Rewrites a natural-language search query into a safe SQLite FTS5 {@code MATCH} expression.
     *
     * @param query raw search query from user or agent
     * @param matchAll {@code true} to join term clauses with {@code AND}, {@code false} for {@code OR}
     * @return valid FTS5 {@code MATCH} query expression, or empty string if no searchable tokens exist
     */
    public static String buildFts5MatchQuery(String query, boolean matchAll) {
        if (query == null || query.isBlank()) {
            return "";
        }
        Set<String> clauses = new LinkedHashSet<>();
        StringBuilder cjkBuffer = new StringBuilder();
        StringBuilder latinBuffer = new StringBuilder();

        int length = query.length();
        for (int i = 0; i < length; ) {
            int cp = query.codePointAt(i);
            i += Character.charCount(cp);

            if (isCjkCodePoint(cp)) {
                flushLatinQueryToken(latinBuffer, clauses);
                cjkBuffer.appendCodePoint(cp);
            } else if (Character.isLetterOrDigit(cp) || cp == '_') {
                flushCjkQueryTokens(cjkBuffer, clauses);
                latinBuffer.appendCodePoint(Character.toLowerCase(cp));
            } else {
                flushCjkQueryTokens(cjkBuffer, clauses);
                flushLatinQueryToken(latinBuffer, clauses);
            }
        }
        flushCjkQueryTokens(cjkBuffer, clauses);
        flushLatinQueryToken(latinBuffer, clauses);

        if (clauses.isEmpty()) {
            return "";
        }
        String operator = matchAll ? " AND " : " OR ";
        return String.join(operator, clauses);
    }

    /**
     * Emits overlapping character bigrams followed by individual character unigrams for a CJK run.
     */
    private static void emitCjkIndexTokens(String cjkRun, StringBuilder out) {
        int[] codePoints = cjkRun.codePoints().toArray();
        if (!out.isEmpty() && out.charAt(out.length() - 1) != ' ') {
            out.append(' ');
        }
        for (int i = 0; i < codePoints.length - 1; i++) {
            out.appendCodePoint(codePoints[i]).appendCodePoint(codePoints[i + 1]).append(' ');
        }
        for (int cp : codePoints) {
            out.appendCodePoint(cp).append(' ');
        }
    }

    /**
     * Flushes buffered CJK query characters into FTS5 bigram or unigram clauses.
     */
    private static void flushCjkQueryTokens(StringBuilder cjkBuffer, Set<String> clauses) {
        if (cjkBuffer.isEmpty()) {
            return;
        }
        int[] codePoints = cjkBuffer.toString().codePoints().toArray();
        cjkBuffer.setLength(0);

        if (codePoints.length == 1) {
            clauses.add("\"" + new String(codePoints, 0, 1) + "\"");
            return;
        }
        List<String> bigrams = new ArrayList<>(codePoints.length - 1);
        for (int i = 0; i < codePoints.length - 1; i++) {
            String bigram =
                    new StringBuilder()
                            .appendCodePoint(codePoints[i])
                            .appendCodePoint(codePoints[i + 1])
                            .toString();
            bigrams.add("\"" + bigram + "\"");
        }
        if (bigrams.size() == 1) {
            clauses.add(bigrams.get(0));
        } else {
            clauses.add("(" + String.join(" AND ", bigrams) + ")");
        }
    }

    /**
     * Flushes buffered Latin/numeric characters into an FTS5 prefix or exact term clause.
     */
    private static void flushLatinQueryToken(StringBuilder latinBuffer, Set<String> clauses) {
        if (latinBuffer.isEmpty()) {
            return;
        }
        String token = latinBuffer.toString().toLowerCase(Locale.ROOT);
        latinBuffer.setLength(0);
        if (token.length() >= 3) {
            clauses.add("\"" + token + "\"*");
        } else {
            clauses.add("\"" + token + "\"");
        }
    }

    /**
     * Determines whether a Unicode code point belongs to Han, Hiragana, Katakana, or Hangul scripts.
     *
     * @param cp Unicode code point
     * @return {@code true} if the code point is a CJK character
     */
    public static boolean isCjkCodePoint(int cp) {
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }
}

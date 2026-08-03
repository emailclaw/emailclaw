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
package ai.emailclaw.emailclaw.util;

import ai.emailclaw.emailclaw.model.FileDiffRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * File diff utility class.
 *
 * <p>Simplified implementation based on the Myers diff algorithm, calculating row-level differences between two texts,
 * generating unified diff output and added/deleted line statistics.
 *
 * <p>Core features:
 * <ul>
 *   <li>Calculate row-level LCS (Longest Common Subsequence) between old and new text</li>
 *   <li>Generate unified diff output with context based on LCS</li>
 *   <li>Count added and deleted lines</li>
 *   <li>Automatically truncate diff output for huge files to prevent UI rendering lag</li>
 * </ul>
 */
public final class FileDiffUtils {

    private static final Logger LOGGER = Logger.getLogger(FileDiffUtils.class.getName());

    /** Diff output lines limit, truncate if exceeded to protect UI performance. */
    private static final int MAX_DIFF_LINES = 500;

    /** Number of context lines before and after each change block in the unified diff. */
    private static final int CONTEXT_LINES = 3;

    /** Maximum number of lines for LCS calculation, skip exact diff if exceeded. */
    private static final int MAX_LINES_FOR_DIFF = 10000;

    private FileDiffUtils() {}

    /**
     * Calculate file modification differences.
     *
     * @param filePath   File path (for display only)
     * @param oldContent File content before modification (null or empty string for new file)
     * @param newContent File content after modification
     * @return File diff record, including added/deleted lines and unified diff text
     */
    public static FileDiffRecord computeDiff(
            String filePath, String oldContent, String newContent) {
        LOGGER.log(Level.FINE, "Start calculating file diff: {0}", filePath);

        // Handle boundary cases: empty old content means new file
        boolean isNewFile = (oldContent == null || oldContent.isEmpty());
        String safeOld = oldContent == null ? "" : oldContent;
        String safeNew = newContent == null ? "" : newContent;

        // If content is completely identical, no difference
        if (safeOld.equals(safeNew)) {
            LOGGER.log(Level.FINE, "File content has not changed: {0}", filePath);
            return new FileDiffRecord(filePath, 0, 0, "", isNewFile);
        }

        String[] oldLines = splitLines(safeOld);
        String[] newLines = splitLines(safeNew);

        // Huge files: only return statistics, no detailed diff
        if (oldLines.length > MAX_LINES_FOR_DIFF || newLines.length > MAX_LINES_FOR_DIFF) {
            LOGGER.log(
                    Level.INFO,
                    "File too large, skipping detailed diff calculation: {0} (Old={1} lines,"
                            + " New={2} lines)",
                    new Object[] {filePath, oldLines.length, newLines.length});
            int added = newLines.length;
            int deleted = oldLines.length;
            String summary =
                    "File too large (old "
                            + oldLines.length
                            + " lines -> new "
                            + newLines.length
                            + " lines), diff calculation skipped.";
            return new FileDiffRecord(filePath, added, deleted, summary, isNewFile);
        }

        // Calculate longest common subsequence (LCS) to get row-level differences
        List<DiffLine> diffLines = computeLineDiff(oldLines, newLines);

        // Count added and deleted lines
        int linesAdded = 0;
        int linesDeleted = 0;
        for (DiffLine dl : diffLines) {
            if (dl.type == DiffType.ADD) {
                linesAdded++;
            } else if (dl.type == DiffType.DELETE) {
                linesDeleted++;
            }
        }

        // Generate unified diff text
        String unifiedDiff = formatUnifiedDiff(diffLines, filePath);

        LOGGER.log(
                Level.INFO,
                "File diff calculation complete: {0}, +{1} -{2}",
                new Object[] {filePath, linesAdded, linesDeleted});

        return new FileDiffRecord(filePath, linesAdded, linesDeleted, unifiedDiff, isNewFile);
    }

    /**
     * Diff line type enumeration.
     */
    private enum DiffType {
        /** Context line (unchanged). */
        CONTEXT,
        /** Added line. */
        ADD,
        /** Deleted line. */
        DELETE
    }

    /**
     * Diff line record.
     *
     * @param type    Line type
     * @param content Line content
     * @param oldNum  Old file line number (only meaningful for CONTEXT and DELETE, -1 for ADD)
     * @param newNum  New file line number (only meaningful for CONTEXT and ADD, -1 for DELETE)
     */
    private record DiffLine(DiffType type, String content, int oldNum, int newNum) {}

    /**
     * Calculate row-level differences between two arrays of lines based on LCS.
     *
     * <p>Use dynamic programming to calculate the longest common subsequence (LCS), and then generate a list of different lines by backtracking.
     *
     * @param oldLines Array of lines of the old file
     * @param newLines Array of lines of the new file
     * @return List of diff lines
     */
    private static List<DiffLine> computeLineDiff(String[] oldLines, String[] newLines) {
        int m = oldLines.length;
        int n = newLines.length;

        // Build LCS table (dynamic programming)
        // Use a short array to reduce memory footprint; it will overflow in extreme cases exceeding
        // 32767 lines,
        // but such files are intercepted by MAX_LINES_FOR_DIFF at the upper layer.
        short[][] dp = new short[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (oldLines[i - 1].equals(newLines[j - 1])) {
                    dp[i][j] = (short) (dp[i - 1][j - 1] + 1);
                } else {
                    dp[i][j] = (short) Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // Backtrack the LCS table and generate a list of different lines
        List<DiffLine> result = new ArrayList<>();
        int i = m;
        int j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines[i - 1].equals(newLines[j - 1])) {
                result.addFirst(new DiffLine(DiffType.CONTEXT, oldLines[i - 1], i, j));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                result.addFirst(new DiffLine(DiffType.ADD, newLines[j - 1], -1, j));
                j--;
            } else {
                result.addFirst(new DiffLine(DiffType.DELETE, oldLines[i - 1], i, -1));
                i--;
            }
        }

        return result;
    }

    /**
     * Format a list of diff lines into a unified diff text with context.
     *
     * <p>Only output changed lines and their context of {@link #CONTEXT_LINES} lines before and after,
     * use {@code @@ ... @@} separator to mark the gap between consecutive context lines.
     *
     * @param diffLines List of diff lines
     * @param filePath  File path (used for diff header)
     * @return Formatted unified diff text
     */
    private static String formatUnifiedDiff(List<DiffLine> diffLines, String filePath) {
        if (diffLines.isEmpty()) {
            return "";
        }

        // Mark which lines need to be output (changed lines and their context)
        boolean[] show = new boolean[diffLines.size()];
        for (int i = 0; i < diffLines.size(); i++) {
            if (diffLines.get(i).type != DiffType.CONTEXT) {
                // Mark the changed line itself and the context before and after
                for (int k = Math.max(0, i - CONTEXT_LINES);
                        k <= Math.min(diffLines.size() - 1, i + CONTEXT_LINES);
                        k++) {
                    show[k] = true;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("--- a/").append(filePath).append('\n');
        sb.append("+++ b/").append(filePath).append('\n');

        int outputLineCount = 0;
        boolean inHunk = false;

        for (int i = 0; i < diffLines.size() && outputLineCount < MAX_DIFF_LINES; i++) {
            if (!show[i]) {
                if (inHunk) {
                    inHunk = false;
                }
                continue;
            }

            // Output separator mark when a new hunk starts
            if (!inHunk) {
                inHunk = true;
                // Calculate hunk range
                int hunkOldStart = -1;
                int hunkNewStart = -1;
                for (int k = i; k < diffLines.size() && show[k]; k++) {
                    DiffLine dl = diffLines.get(k);
                    if (hunkOldStart < 0 && dl.oldNum > 0) {
                        hunkOldStart = dl.oldNum;
                    }
                    if (hunkNewStart < 0 && dl.newNum > 0) {
                        hunkNewStart = dl.newNum;
                    }
                    if (hunkOldStart > 0 && hunkNewStart > 0) {
                        break;
                    }
                }
                sb.append("@@ -")
                        .append(Math.max(1, hunkOldStart))
                        .append(" +")
                        .append(Math.max(1, hunkNewStart))
                        .append(" @@\n");
                outputLineCount++;
            }

            DiffLine dl = diffLines.get(i);
            switch (dl.type) {
                case CONTEXT -> sb.append(' ').append(dl.content).append('\n');
                case ADD -> sb.append('+').append(dl.content).append('\n');
                case DELETE -> sb.append('-').append(dl.content).append('\n');
            }
            outputLineCount++;
        }

        // If diff is truncated, add a prompt
        if (outputLineCount >= MAX_DIFF_LINES) {
            sb.append("\n... (Diff output truncated, total ")
                    .append(diffLines.size())
                    .append(" diff lines) ...\n");
        }

        return sb.toString();
    }

    /**
     * Split text into an array of lines by line breaks.
     *
     * @param text Input text
     * @return Array of lines (excluding trailing empty line, but keeping middle empty lines)
     */
    private static String[] splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        return text.split("\n", -1);
    }
}

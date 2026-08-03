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

import java.net.URLDecoder;
import java.util.logging.Logger;

/**
 * File name and path sanitization utility class.
 *
 * <p>Unifies scattered file name/path sanitization logic in the project, eliminating duplicate implementations and ensuring consistent behavior.
 *
 * <p>Provides two sanitization strategies:
 * <ul>
 *   <li>{@link #sanitizeEnglishPathName(String)} - Strict mode: retains letters, numbers, dots, underscores, hyphens,
 *       and replaces other characters with underscores. Suitable for scenarios like internal attachment storage.</li>
 *   <li>{@link #sanitizePathName(String)} - Lenient mode: only replaces OS invalid file name characters
 *       ({@code \ / : * ? " < > |}) and whitespace characters with underscores. Suitable for external file path processing.</li>
 * </ul>
 *
 * <p>Typical usage:
 * <pre>{@code
 * // Internal attachment file name
 * String safe = FileNameUtils.sanitizeFileName("My File (1).txt");
 * // Result: "My_File__1_.txt"
 *
 * // External file path
 * String path = FileNameUtils.sanitizeFilePath("C:\\Users\\test\\file.txt");
 * // Result: "C:\\Users\\test\\file.txt" (No modification needed)
 * }</pre>
 */
public final class FileNameUtils {

    private static final Logger LOGGER = Logger.getLogger(FileNameUtils.class.getName());

    /**
     * Empty file name (used when input is empty or sanitized to empty). FileNameUtils does not return a specific default value, caller is responsible.
     */
    private static final String EMPTY_FILENAME = "";

    /**
     * Default maximum file name length.
     */
    private static final int DEFAULT_MAX_LENGTH = 50;

    /**
     * Strict mode: retains letters, numbers, dots, underscores, hyphens.
     */
    private static final String ENGLISH_PATTERN = "[^A-Za-z0-9._-]";

    /**
     * Lenient mode: replaces OS invalid file name characters and whitespace.
     */
    private static final String OS_ILLEGAL_PATTERN = "[\\\\/:*?\"<>|\\s]+";

    /**
     * Private constructor to prevent instantiation.
     */
    private FileNameUtils() {}

    /**
     * Strict mode sanitize file name.
     *
     * <p>Retains letters, numbers, dots ({@code .}), underscores ({@code _}), hyphens ({@code -}),
     * and replaces all other characters with underscores ({@code _}). Consecutive invalid characters are merged into a single underscore.
     *
     * <p>Applicable scenarios:
     * <ul>
     *   <li>Internal attachment storage file names</li>
     *   <li>Scenarios requiring file names without special characters</li>
     *   <li>Identifiers such as Skill names, Agent IDs</li>
     * </ul>
     *
     * @param name Original file name
     * @return Sanitized file name, maximum length {@value DEFAULT_MAX_LENGTH} characters
     */
    public static String sanitizeEnglishPathName(String name) {
        return sanitizeEnglishPathName(name, DEFAULT_MAX_LENGTH);
    }

    /**
     * Sanitize directory name (replace illegal characters and spaces).
     *
     * @param name        Original name
     * @param defaultName Default name when empty
     * @return Sanitized directory name
     */
    public static String sanitizeEnglishPathName(String name, String defaultName) {
        String sanitezedName = sanitizeEnglishPathName(name);
        return (sanitezedName == null || sanitezedName.isBlank()) ? defaultName : sanitezedName;
    }

    /**
     * Strict mode sanitize file name (specify maximum length).
     *
     * @param name      Original file name
     * @param maxLength Maximum allowed length
     * @return Sanitized file name
     */
    public static String sanitizeEnglishPathName(String name, int maxLength) {
        if (name == null || name.isBlank()) {
            LOGGER.fine("File name is empty: " + EMPTY_FILENAME);
            return EMPTY_FILENAME;
        }
        String safe = name.trim().replaceAll(ENGLISH_PATTERN, "_");
        // Merge consecutive underscores
        safe = safe.replaceAll("_+", "_");
        // Remove leading and trailing underscores
        safe = safe.replaceAll("^_|_$", "");
        if (safe.isBlank()) {
            LOGGER.fine("File name is empty after sanitization: " + EMPTY_FILENAME);
            return EMPTY_FILENAME;
        }
        if (safe.length() > maxLength) {
            safe = safe.substring(0, maxLength);
        }
        return safe;
    }

    /**
     * Lenient mode sanitize file path.
     *
     * <p>Only replaces OS invalid file name characters ({@code \ / : * ? " < > |}) and whitespace characters with underscores,
     * retaining all other characters (including Chinese, special symbols, etc.).
     *
     * <p>Applicable scenarios:
     * <ul>
     *   <li>External file path processing (e.g., email attachments)</li>
     *   <li>Scenarios requiring retention of as much original information as possible</li>
     *   <li>Cross-platform file path compatibility</li>
     * </ul>
     *
     * @param name Original file path
     * @return Sanitized file path, maximum length {@value DEFAULT_MAX_LENGTH} characters
     */
    public static String sanitizePathName(String name) {
        return sanitizePathName(name, DEFAULT_MAX_LENGTH);
    }

    /**
     * Lenient mode sanitize file path (specify maximum length).
     *
     * @param name      Original file path
     * @param maxLength Maximum allowed length
     * @return Sanitized file path
     */
    public static String sanitizePathName(String name, int maxLength) {
        if (name == null || name.isBlank()) {
            LOGGER.fine("File path is empty: " + EMPTY_FILENAME);
            return EMPTY_FILENAME;
        }
        String safe = name.trim().replaceAll(OS_ILLEGAL_PATTERN, "_");
        // Merge consecutive underscores
        safe = safe.replaceAll("_+", "_");
        // Remove leading and trailing underscores
        safe = safe.replaceAll("^_|_$", "");
        if (safe.isBlank()) {
            LOGGER.fine("File path is empty after sanitization: " + EMPTY_FILENAME);
            return EMPTY_FILENAME;
        }
        if (safe.length() > maxLength) {
            safe = safe.substring(0, maxLength);
        }
        return safe;
    }

    /**
     * Sanitize directory name (replace illegal characters and spaces).
     *
     * @param name        Original name
     * @param defaultName Default name when empty
     * @return Sanitized directory name
     */
    public static String sanitizePathName(String name, String defaultName) {
        String sanitezedName = sanitizePathName(name);
        return (sanitezedName == null || sanitezedName.isBlank()) ? defaultName : sanitezedName;
    }

    /**
     * Sanitize and truncate file name, while preserving extension.
     *
     * <p>Based on strict mode sanitization, additionally processes the dot-separated extension, ensuring the extension is not truncated.
     *
     * @param name      Original file name
     * @param maxLength Maximum allowed length (including extension)
     * @return Sanitized file name
     */
    public static String sanitizeFileNamePreserveExtension(String name, int maxLength) {
        if (name == null || name.isBlank()) {
            return EMPTY_FILENAME;
        }
        String trimmed = name.trim();
        int dotIndex = trimmed.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex >= trimmed.length() - 1) {
            // No valid extension, sanitize directly
            return sanitizeEnglishPathName(trimmed, maxLength);
        }
        String stem = trimmed.substring(0, dotIndex);
        String ext = trimmed.substring(dotIndex);
        // Extension also needs sanitization (retaining dot)
        String safeExt = ext.replaceAll("[^A-Za-z0-9._-]", "_");
        int stemMax = maxLength - safeExt.length();
        if (stemMax <= 0) {
            return sanitizeEnglishPathName(stem, maxLength);
        }
        String safeStem = sanitizeEnglishPathName(stem, stemMax);
        return safeStem + safeExt;
    }

    /**
     * Extract and sanitize file name from full path.
     *
     * <p>Handles path separators, URL encoding, etc., extracts the final file name part and sanitizes it.
     *
     * @param path Full file path
     * @return Sanitized file name
     */
    public static String extractAndSanitizeFileName(String path) {
        if (path == null || path.isBlank()) {
            return EMPTY_FILENAME;
        }
        // Handle URL encoding
        String decoded = path;
        try {
            decoded = URLDecoder.decode(path, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Decoding failed, use original path
            LOGGER.fine("URL decoding failed: " + path);
        }
        // Extract file name part
        String fileName = decoded;
        int lastSlash = Math.max(decoded.lastIndexOf('/'), decoded.lastIndexOf('\\'));
        if (lastSlash >= 0 && lastSlash < decoded.length() - 1) {
            fileName = decoded.substring(lastSlash + 1);
        }
        return sanitizeEnglishPathName(fileName);
    }
}

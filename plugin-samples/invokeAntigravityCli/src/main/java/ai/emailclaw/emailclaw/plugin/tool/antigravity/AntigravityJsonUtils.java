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
package ai.emailclaw.emailclaw.plugin.tool.antigravity;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Utility functions for JSON prompt decoration, Markdown code fence stripping, and structured output parsing
 * for the Antigravity CLI plugin.
 */
public final class AntigravityJsonUtils {

    private static final Logger LOGGER = Logger.getLogger(AntigravityJsonUtils.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Strict JSON output prompt suffix required for headless structured execution.
     */
    public static final String JSON_FORMAT_INSTRUCTION =
            "Please output the result strictly in valid JSON format without Markdown code block"
                    + " markers.";

    private AntigravityJsonUtils() {}

    /**
     * Appends the strict JSON format instruction to the end of the prompt if not already present.
     *
     * @param prompt The original user/agent prompt
     * @return The prompt guaranteed to end with the required JSON formatting instruction
     */
    public static String appendJsonFormatInstruction(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return JSON_FORMAT_INSTRUCTION;
        }
        String trimmed = prompt.trim();
        if (trimmed.endsWith(JSON_FORMAT_INSTRUCTION)) {
            return trimmed;
        }
        return trimmed + "\n\n" + JSON_FORMAT_INSTRUCTION;
    }

    /**
     * Extracts clean, valid JSON from CLI output, removing any accidental Markdown code blocks (e.g. ```json ... ```).
     *
     * @param rawOutput The raw text output from the CLI process
     * @return Clean JSON string
     */
    public static String extractCleanJson(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return "{}";
        }
        String text = rawOutput.trim();

        // 1. Strip leading and trailing Markdown code fences
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline != -1) {
                text = text.substring(firstNewline + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3).trim();
            }
        }

        // 2. Check if the stripped text is directly valid JSON
        try {
            JsonNode node = MAPPER.readTree(text);
            if (node != null) {
                return text;
            }
        } catch (Exception ignored) {
            // Continue scanning for nested/surrounded JSON structures
        }

        // 3. Scan for outermost JSON object { ... }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            String candidate = text.substring(firstBrace, lastBrace + 1).trim();
            try {
                JsonNode node = MAPPER.readTree(candidate);
                if (node != null) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // Continue scanning
            }
        }

        // 4. Scan for outermost JSON array [ ... ]
        int firstBracket = text.indexOf('[');
        int lastBracket = text.lastIndexOf(']');
        if (firstBracket != -1 && lastBracket > firstBracket) {
            String candidate = text.substring(firstBracket, lastBracket + 1).trim();
            try {
                JsonNode node = MAPPER.readTree(candidate);
                if (node != null) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // Continue scanning
            }
        }

        // 5. If not parseable as JSON, wrap raw text into a valid JSON object envelope
        try {
            return MAPPER.writeValueAsString(Map.of("success", true, "rawOutput", text));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to wrap raw output into JSON envelope", e);
            return "{\"success\":true,\"rawOutput\":\"" + escapeJson(text) + "\"}";
        }
    }

    /**
     * Creates a standardized JSON error response.
     *
     * @param errorMessage Descriptive error message
     * @param exitCode Process exit code
     * @return JSON string representing the error
     */
    public static String createErrorJson(String errorMessage, int exitCode) {
        try {
            return MAPPER.writeValueAsString(
                    Map.of(
                            "success",
                            false,
                            "error",
                            errorMessage != null ? errorMessage : "Unknown error",
                            "exitCode",
                            exitCode));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to serialize error response to JSON", e);
            return "{\"success\":false,\"error\":\""
                    + escapeJson(errorMessage)
                    + "\",\"exitCode\":"
                    + exitCode
                    + "}";
        }
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

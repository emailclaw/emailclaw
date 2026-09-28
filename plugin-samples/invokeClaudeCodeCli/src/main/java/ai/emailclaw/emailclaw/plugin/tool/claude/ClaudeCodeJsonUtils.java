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
package ai.emailclaw.emailclaw.plugin.tool.claude;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * Utility functions for Markdown code fence stripping, JSON Lines (NDJSON) aggregation,
 * and structured output parsing for the Claude Code CLI plugin.
 */
public final class ClaudeCodeJsonUtils {

    private static final Logger LOGGER = Logger.getLogger(ClaudeCodeJsonUtils.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ClaudeCodeJsonUtils() {}

    /**
     * Extracts clean, valid JSON from Claude Code CLI output, removing any Markdown code fences
     * (e.g. ```json ... ```) or converting newline-delimited JSON (NDJSON) event streams into a JSON array.
     *
     * @param rawOutput The raw text output from the Claude Code CLI process
     * @return Clean JSON string guaranteed to parse as valid JSON
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

        // 2. Check if the stripped text is directly valid JSON (Object or Array)
        try {
            JsonNode node = MAPPER.readTree(text);
            if (node != null && (node.isObject() || node.isArray())) {
                return text;
            }
        } catch (Exception ignored) {
            // Continue scanning for alternative JSON structures
        }

        // 3. Check for newline-delimited JSON (NDJSON / JSON Lines) output
        String[] lines = text.split("\\R");
        if (lines.length > 1) {
            ArrayNode arrayNode = MAPPER.createArrayNode();
            boolean allLinesValidJson = true;
            int jsonLineCount = 0;
            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty()) {
                    continue;
                }
                try {
                    JsonNode lineNode = MAPPER.readTree(trimmedLine);
                    if (lineNode != null && (lineNode.isObject() || lineNode.isArray())) {
                        arrayNode.add(lineNode);
                        jsonLineCount++;
                    } else {
                        allLinesValidJson = false;
                        break;
                    }
                } catch (Exception e) {
                    allLinesValidJson = false;
                    break;
                }
            }
            if (allLinesValidJson && jsonLineCount > 0) {
                try {
                    return MAPPER.writeValueAsString(arrayNode);
                } catch (Exception ignored) {
                    // Fall through
                }
            }
        }

        // 4. Scan for outermost JSON object { ... }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            String candidate = text.substring(firstBrace, lastBrace + 1).trim();
            try {
                JsonNode node = MAPPER.readTree(candidate);
                if (node != null && node.isObject()) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // Continue scanning
            }
        }

        // 5. Scan for outermost JSON array [ ... ]
        int firstBracket = text.indexOf('[');
        int lastBracket = text.lastIndexOf(']');
        if (firstBracket != -1 && lastBracket > firstBracket) {
            String candidate = text.substring(firstBracket, lastBracket + 1).trim();
            try {
                JsonNode node = MAPPER.readTree(candidate);
                if (node != null && node.isArray()) {
                    return candidate;
                }
            } catch (Exception ignored) {
                // Continue scanning
            }
        }

        // 6. If not parseable as JSON, wrap raw text into a valid JSON object envelope
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
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

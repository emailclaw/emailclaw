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
package ai.emailclaw.emailclaw.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Generic JSON persistence tool.
 *
 * <p>Shields JSON read/write details, providing:
 * <ul>
 *   <li>Basic read/write ({@link #read}, {@link #readList}, {@link #write})</li>
 *   <li>Read/write with content fingerprints (for change detection during hot-loading)</li>
 *   <li>Atomic writes (write to temp file then rename, preventing file corruption from interrupted writes)</li>
 * </ul>
 *
 * <p>Returns fallback values in all read failure scenarios, reducing upper-layer null check branches.
 */
public class JsonStore {
    private static final Logger LOGGER = Logger.getLogger(JsonStore.class.getName());
    private final ObjectMapper mapper;

    public JsonStore() {
        mapper = new ObjectMapper();
    }

    // ======================== Basic Read ========================

    public <T> T read(Path path, Class<T> type) {
        return read(path, type, null);
    }

    public <T> T read(Path path, Class<T> type, T fallback) {
        if (!Files.exists(path)) {
            LOGGER.log(Level.FINE, "Read skipped, file does not exist: {0}", path);
            return fallback;
        }
        try {
            return mapper.readValue(path.toFile(), type);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read JSON, returning fallback: " + path, e);
            return fallback;
        }
    }

    public <T> List<T> readList(String fileContent, TypeReference<List<T>> typeRef) {
        if (fileContent == null || fileContent.isBlank()) {
            LOGGER.log(Level.FINE, "List read skipped, content is empty");
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(fileContent, typeRef);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read JSON list, returning empty list", e);
            return new ArrayList<>();
        }
    }

    public <T> List<T> readList(Path path, TypeReference<List<T>> typeRef) {
        if (!Files.exists(path)) {
            LOGGER.log(Level.FINE, "List read skipped, file does not exist: {0}", path);
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(path.toFile(), typeRef);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read JSON list, returning empty list: " + path, e);
            return new ArrayList<>();
        }
    }

    // ======================== Read with content fingerprint ========================

    /**
     * Read JSON object, and return the original file content (for fingerprint comparison).
     *
     * @param path     File path
     * @param type     Target type
     * @param fallback Fallback value when file does not exist or parsing fails
     * @return Read result
     * @see WriteResult Used to simultaneously return written content and file content after writing
     */
    public <T> ReadResult<T> readWithContent(Path path, Class<T> type, T fallback) {
        if (!Files.exists(path)) {
            return new ReadResult<>(fallback, "");
        }
        try {
            String content = Files.readString(path);
            T value = content.isBlank() ? fallback : mapper.readValue(content, type);
            return new ReadResult<>(value == null ? fallback : value, content);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read JSON: " + path, e);
            return new ReadResult<>(fallback, "");
        }
    }

    /**
     * Read JSON list, and return original file content (for fingerprint comparison).
     */
    public <T> ReadResult<List<T>> readListWithContent(Path path, TypeReference<List<T>> typeRef) {
        if (!Files.exists(path)) {
            return new ReadResult<>(new ArrayList<>(), "");
        }
        try {
            String content = Files.readString(path);
            List<T> list =
                    content.isBlank() ? new ArrayList<>() : mapper.readValue(content, typeRef);
            return new ReadResult<>(list == null ? new ArrayList<>() : list, content);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read JSON list: " + path, e);
            return new ReadResult<>(new ArrayList<>(), "");
        }
    }

    // ======================== Basic Write ========================

    public void write(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
            LOGGER.log(Level.FINE, "Successfully wrote JSON: {0}", path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON file: " + path, e);
        }
    }

    // ======================== Write with content fingerprint ========================

    /**
     * Atomically write JSON object.
     *
     * <p>Serialize to JSON string first before writing to temporary file and renaming,
     * preventing file corruption caused by interrupted writes.
     *
     * @param path  File path
     * @param value Object to serialize
     * @return Written JSON string content
     */
    public String writeAtomic(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            String content = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
            writeStringAtomically(path, content);
            return content;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON file: " + path, e);
        }
    }

    /**
     * Content fingerprint deduplication + atomic write.
     *
     * <p>If the serialized content is the same as {@code lastWrittenContent}, skip the actual write,
     * avoiding unnecessary disk I/O and file system event triggers.
     *
     * @param path                File path
     * @param value               Object to serialize
     * @param lastWrittenContent  Fingerprint of the last successful write (can be null)
     * @return Actual written content (returns lastWrittenContent if skipped)
     */
    public String writeIfChanged(Path path, Object value, String lastWrittenContent) {
        try {
            String content = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
            if (lastWrittenContent != null && lastWrittenContent.equals(content)) {
                return lastWrittenContent;
            }
            Files.createDirectories(path.getParent());
            writeStringAtomically(path, content);
            return content;
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON file: " + path, e);
        }
    }

    // ======================== Format Error Message ========================

    /**
     * Format JSON parsing error message.
     *
     * <p>Distinguish common JSON format error types to provide clearer diagnostic information.
     *
     * @param file      Config file path
     * @param exception Caught exception
     * @return Formatted error message
     */
    public String formatErrorMessage(Path file, Exception exception) {
        String filename = file.getFileName().toString();
        String errorType = exception.getClass().getSimpleName();
        if (exception.getMessage() != null) {
            String msg = exception.getMessage();
            if (msg.contains("Unexpected character") || msg.contains("JSON")) {
                return "JSON format error: The JSON structure of file "
                        + filename
                        + " is corrupted, please check the syntax.";
            } else if (msg.contains("Cannot deserialize")) {
                return "JSON data type mismatch: The field types in file "
                        + filename
                        + " do not match expectations.";
            }
        }
        return "Failed to read file: " + filename + " (" + errorType + ")";
    }

    // ======================== Content Parsing ========================

    /**
     * Parse object from JSON string (does not read file, only parses).
     */
    public <T> T parse(String json, Class<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            T value = mapper.readValue(json, type);
            return value == null ? fallback : value;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse JSON string", e);
            return fallback;
        }
    }

    /**
     * Parse list from JSON string (does not read file, only parses).
     */
    public <T> List<T> parseList(String json, TypeReference<List<T>> typeRef) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<T> list = mapper.readValue(json, typeRef);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse JSON list string", e);
            return new ArrayList<>();
        }
    }

    /**
     * Serialize object to JSON string (does not write file, only serializes).
     */
    public String toJson(Object value) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JSON", e);
        }
    }

    // ======================== Internal Tools ========================

    private void writeStringAtomically(Path file, String content) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp =
                Files.createTempFile(
                        parent == null ? Path.of(".") : parent,
                        file.getFileName().toString(),
                        ".tmp");
        try {
            Files.writeString(temp, content);
            try {
                Files.move(
                        temp,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    // ======================== Data Classes ========================

    /** Read result with content fingerprint. */
    public record ReadResult<T>(T value, String content) {}

    /** Write result with content fingerprint. */
    public record WriteResult<T>(T value, String content) {}
}

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
package ai.emailclaw.emailclaw.model;

/**
 * Immutable artifact record representing a deliverable produced by an Agent.
 *
 * @param id Unique artifact identifier (UUID)
 * @param projectId Associated project ID
 * @param sessionId Associated chat session ID
 * @param agentId Producing agent ID
 * @param channel Originating channel ID (e.g. email, console, cron)
 * @param fileName Target plain file name (without path separators)
 * @param sourcePath Normalized source path inside the agent workspace or project
 * @param storagePath Absolute storage path on the host filesystem
 * @param mimeType MIME content type (e.g. application/pdf, image/png)
 * @param sizeBytes File content size in bytes
 * @param sha256 SHA-256 checksum for audit and deduplication
 * @param description Short description provided by the agent
 * @param createdAtMs Creation timestamp in epoch milliseconds
 */
public record ArtifactRecord(
        String id,
        String projectId,
        String sessionId,
        String agentId,
        String channel,
        String fileName,
        String sourcePath,
        String storagePath,
        String mimeType,
        long sizeBytes,
        String sha256,
        String description,
        long createdAtMs) {}

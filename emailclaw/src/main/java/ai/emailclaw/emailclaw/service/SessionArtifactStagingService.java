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
package ai.emailclaw.emailclaw.service;

import ai.emailclaw.emailclaw.model.ArtifactRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe staging service that temporarily buffers {@link ArtifactRecord} deliverables produced
 * during active session execution turns until consumed by outbound delivery channels.
 */
public class SessionArtifactStagingService {

    private static final Logger LOGGER =
            Logger.getLogger(SessionArtifactStagingService.class.getName());

    private final ConcurrentHashMap<String, List<ArtifactRecord>> stagingMap =
            new ConcurrentHashMap<>();

    public SessionArtifactStagingService() {
        LOGGER.info("SessionArtifactStagingService initialized");
    }

    /**
     * Stages a newly delivered artifact under the specified session ID.
     *
     * @param sessionId active session identifier
     * @param artifact artifact record to stage
     */
    public void stageArtifact(String sessionId, ArtifactRecord artifact) {
        if (sessionId == null || artifact == null) {
            return;
        }
        stagingMap
                .computeIfAbsent(sessionId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(artifact);
        LOGGER.log(
                Level.INFO,
                "Staged artifact for session {0}: {1} ({2} bytes)",
                new Object[] {sessionId, artifact.fileName(), artifact.sizeBytes()});
    }

    /**
     * Reads all currently staged artifacts for a session without clearing them.
     *
     * @param sessionId active session identifier
     * @return unmodifiable copy of staged artifacts
     */
    public List<ArtifactRecord> getStagedArtifacts(String sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        List<ArtifactRecord> list = stagingMap.get(sessionId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    /**
     * Atomically drains and clears all staged artifacts for a session.
     *
     * @param sessionId active session identifier
     * @return unmodifiable list of drained artifacts
     */
    public List<ArtifactRecord> drainStagedArtifacts(String sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        List<ArtifactRecord> list = stagingMap.remove(sessionId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        synchronized (list) {
            LOGGER.log(
                    Level.INFO,
                    "Drained {0} staged artifact(s) for session {1}",
                    new Object[] {list.size(), sessionId});
            return List.copyOf(list);
        }
    }

    /**
     * Clears any staged artifacts for a session without returning them.
     *
     * @param sessionId active session identifier
     */
    public void clear(String sessionId) {
        if (sessionId != null) {
            stagingMap.remove(sessionId);
        }
    }
}

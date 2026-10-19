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

import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ArtifactRecord;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.ProjectInfo;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.sqlite.SqliteArtifactRepository;
import ai.emailclaw.emailclaw.util.DateTimeUtils;
import ai.emailclaw.emailclaw.util.FileNameUtils;
import ai.emailclaw.emailclaw.util.UuidUtils;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.artifact.ArtifactDeliveryRequest;
import io.agentscope.harness.agent.artifact.ArtifactDeliveryResult;
import io.agentscope.harness.agent.artifact.ArtifactDeliveryTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Industrial-grade implementation of AgentScope's {@link ArtifactDeliveryTarget} for Emailclaw.
 *
 * <p>Receives artifact delivery requests from the agent's {@code deliver_artifact} tool, stores file
 * bytes into the project's standardized artifacts directory, persists audit metadata in SQLite, and
 * stages the artifact for immediate outbound transmission by active channels (e.g. email attachment).
 */
public class EmailclawArtifactDeliveryTarget implements ArtifactDeliveryTarget {

    private static final Logger LOGGER =
            Logger.getLogger(EmailclawArtifactDeliveryTarget.class.getName());

    private final AppContext repository;
    private final ProjectService projectService;
    private final SqliteArtifactRepository artifactRepository;
    private final SessionArtifactStagingService stagingService;

    public EmailclawArtifactDeliveryTarget(
            AppContext repository,
            ProjectService projectService,
            SqliteArtifactRepository artifactRepository,
            SessionArtifactStagingService stagingService) {
        this.repository = Objects.requireNonNull(repository, "AppContext cannot be null");
        this.projectService =
                Objects.requireNonNull(projectService, "ProjectService cannot be null");
        this.artifactRepository =
                Objects.requireNonNull(
                        artifactRepository, "SqliteArtifactRepository cannot be null");
        this.stagingService =
                Objects.requireNonNull(
                        stagingService, "SessionArtifactStagingService cannot be null");
        LOGGER.info("EmailclawArtifactDeliveryTarget initialized");
    }

    @Override
    public ArtifactDeliveryResult deliver(
            RuntimeContext runtimeContext, ArtifactDeliveryRequest request) {
        if (request == null || request.content() == null) {
            return ArtifactDeliveryResult.fail("Artifact request or content is null");
        }

        String sessionId =
                runtimeContext != null && runtimeContext.getSessionId() != null
                        ? runtimeContext.getSessionId()
                        : "default";

        ChatSessionInfo session =
                repository != null && sessionId != null
                        ? repository.sessionRepository().findById(sessionId).orElse(null)
                        : null;

        ProjectInfo project =
                runtimeContext != null && runtimeContext.get(ProjectInfo.class) != null
                        ? runtimeContext.get(ProjectInfo.class)
                        : (session != null && !session.getProjectId().isBlank()
                                ? projectService.findById(session.getProjectId())
                                : projectService.currentDefault());

        AgentInfo agent = runtimeContext != null ? runtimeContext.get(AgentInfo.class) : null;
        String agentId =
                agent != null && agent.getId() != null
                        ? agent.getId()
                        : (session != null && session.getAgentId() != null
                                ? session.getAgentId()
                                : "default");

        String channel =
                session != null && session.getChannel() != null && !session.getChannel().isBlank()
                        ? session.getChannel()
                        : "console";

        String fileName = request.fileName();
        LOGGER.log(
                Level.INFO,
                "Processing deliver_artifact: session={0}, project={1}, file={2}, bytes={3},"
                        + " force={4}",
                new Object[] {
                    sessionId, project.getId(), fileName, request.content().length, request.force()
                });

        try {
            Path projectBase =
                    Path.of(FileNameUtils.expandUserHome(project.getBaseDirectory()))
                            .toAbsolutePath()
                            .normalize();
            Path artifactsDir = projectBase.resolve("artifacts").normalize();
            Files.createDirectories(artifactsDir);

            Path targetPath = artifactsDir.resolve(fileName).normalize();

            if (Files.exists(targetPath) && !request.force()) {
                LOGGER.log(
                        Level.WARNING,
                        "Artifact conflict: file ''{0}'' already exists in project artifacts"
                                + " directory: {1}",
                        new Object[] {fileName, targetPath});
                return ArtifactDeliveryResult.conflict(
                        "File '"
                                + fileName
                                + "' already exists in project artifacts directory. Use force=true"
                                + " to overwrite, or choose a different fileName.");
            }

            Files.write(targetPath, request.content());

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(request.content());
            String sha256 = HexFormat.of().formatHex(hashBytes);

            String mimeType = Files.probeContentType(targetPath);
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "application/octet-stream";
            }

            String artifactId = UuidUtils.randomUUIDv7().toString();
            long now = DateTimeUtils.currentTimeMillis();

            ArtifactRecord record =
                    new ArtifactRecord(
                            artifactId,
                            project.getId(),
                            sessionId,
                            agentId,
                            channel,
                            fileName,
                            request.filePath(),
                            targetPath.toString(),
                            mimeType,
                            request.content().length,
                            sha256,
                            request.description(),
                            now);

            artifactRepository.save(record);
            stagingService.stageArtifact(sessionId, record);

            LOGGER.log(
                    Level.INFO,
                    "Delivered artifact successfully: id={0}, targetPath={1}, sha256={2}",
                    new Object[] {artifactId, targetPath, sha256});

            return ArtifactDeliveryResult.success(
                    String.format(
                            "Delivered '%s' (%d bytes) to project artifacts repository.",
                            fileName, request.content().length));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to deliver artifact: " + fileName, e);
            return ArtifactDeliveryResult.fail("Internal delivery error: " + e.getMessage());
        }
    }
}

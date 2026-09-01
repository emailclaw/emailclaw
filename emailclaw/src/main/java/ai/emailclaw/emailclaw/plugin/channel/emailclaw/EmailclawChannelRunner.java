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
package ai.emailclaw.emailclaw.plugin.channel.emailclaw;

import ai.emailclaw.emailclaw.channel.ChannelIds;
import ai.emailclaw.emailclaw.model.AgentInfo;
import ai.emailclaw.emailclaw.model.ChannelInfo;
import ai.emailclaw.emailclaw.model.ChatMessagePart;
import ai.emailclaw.emailclaw.model.ChatMessageRecord;
import ai.emailclaw.emailclaw.model.ChatMessageRoles;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.ProviderInfo;
import ai.emailclaw.emailclaw.model.security.PendingApproval;
import ai.emailclaw.emailclaw.service.AgentService;
import ai.emailclaw.emailclaw.service.ChannelService;
import ai.emailclaw.emailclaw.service.ChatService;
import ai.emailclaw.emailclaw.service.MessageMarkupTags;
import ai.emailclaw.emailclaw.service.ProjectService;
import ai.emailclaw.emailclaw.service.ProviderService;
import ai.emailclaw.emailclaw.service.StreamCallback;
import ai.emailclaw.emailclaw.service.security.GovernanceService;
import ai.emailclaw.emailclaw.storage.AppHomeConstants;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import ai.emailclaw.emailclaw.storage.WorkspacePaths;
import ai.emailclaw.emailclaw.util.FileNameUtils;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionRule;
import jakarta.mail.Address;
import jakarta.mail.Authenticator;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.FlagTerm;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emailclaw background multi-mailbox polling and dispatch executor.
 * Supports multiple mailbox accounts with session-driven Agent resolution.
 */
public class EmailclawChannelRunner {

    private static final Logger LOGGER = Logger.getLogger(EmailclawChannelRunner.class.getName());

    // ======================== Constants Area ========================
    /** Maximum concurrent mail processing threads per mailbox dispatch */
    private static final int MAIL_PROCESSING_MAX_CONCURRENCY = 4;

    /** Attachment size limit: 10MB */
    private static final long EMAIL_ATTACHMENT_MAX_BYTES = 10L * 1024L * 1024L;

    /** Email address extraction regex */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+)");

    /** Quoted prefix line detection: > ＞ | │ ┃ ¦ */
    private static final Pattern QUOTED_PREFIX_LINE =
            Pattern.compile("^\\s{0,6}(?:[>＞]|\\|\\s|│|┃|¦).*$");

    /** English reply marker: On ... wrote: */
    private static final Pattern EN_REPLY_WROTE_LINE =
            Pattern.compile("(?i)^\\s*on\\s+.+\\s+wrote\\s*:\\s*$");

    /** Chinese reply marker */
    private static final Pattern ZH_REPLY_WROTE_LINE =
            Pattern.compile("(?i)^\\s*on\\s+.+\\s+wrote\\s*:\\s*$");

    /** English original message marker */
    private static final Pattern ORIGINAL_MESSAGE_LINE =
            Pattern.compile(
                    "(?i)^\\s*-{2,}\\s*(?:original message|forwarded message)\\s*-{2,}\\s*$");

    /** Chinese original message marker */
    private static final Pattern ZH_ORIGINAL_MESSAGE_LINE =
            Pattern.compile("^\\s*-{2,}\\s*(?:原始邮件|转发邮件)\\s*-{2,}\\s*$");

    /** RFC822 header start line (English) */
    private static final Pattern RFC822_HEADER_LINE =
            Pattern.compile("(?i)^\\s*(?:from|sent|to|subject|cc|date|reply-to)\\s*:");

    /** RFC822 header start line (Chinese) */
    private static final Pattern ZH_RFC822_HEADER_LINE =
            Pattern.compile("^\\s*(?:发件人|发送时间|收件人|主题|抄送|日期|答复至)\\s*[：:]");

    /** Signature separator line: -- */
    private static final Pattern SIGNATURE_SEPARATOR_LINE = Pattern.compile("^\\s*--\\s*$");

    /** Long separator line: continuous - _ = (at least 6) */
    private static final Pattern LONG_SEPARATOR_LINE = Pattern.compile("^\\s*[-_=]{6,}\\s*$");

    /** Used for multiple spaces folding */
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");

    /** Attachment tag pattern */
    private static final Pattern ATTACHMENT_TAG_PATTERN =
            Pattern.compile("^" + MessageMarkupTags.ATTACHMENT_PATTERN + "$", Pattern.MULTILINE);

    /** Guide reply email template */
    private static final String BOOTSTRAP_GUIDANCE_TEMPLATE =
            "For security reasons, the system only processes emails with valid TaskId in the"
                + " subject line. This email has now been updated with the newly created TaskId."
                + " Please reply to this email, and the system will immediately start working for"
                + " you.";

    /** Attachment error: file not found */
    private static final String ATTACHMENT_ERR_NOT_FOUND = "[Attachment Error: File not found: %s]";

    /** Attachment error: invalid path */
    private static final String ATTACHMENT_ERR_INVALID_PATH =
            "[Attachment Error: Invalid path: %s]";

    /** Attachment directory name */
    private static final String ATTACHMENTS_DIR_NAME = WorkspacePaths.ATTACHMENTS_DIR;

    /** IMAP/SMTP connection timeout (ms) */
    private static final String MAIL_TIMEOUT = "15000";

    /** Sender display name */
    private static final String FROM_NAME = "Emailclaw";

    // ======================== Instance Fields ========================
    private final ChannelService channelService;
    private final ChatService chatService;
    private final AgentService agentService;
    private final ProviderService providerService;
    private final ConfigManager configManager;
    private final ProjectService projectService;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Semaphore mailProcessingPermits = new Semaphore(MAIL_PROCESSING_MAX_CONCURRENCY);

    /**
     * In-memory cache for processed IMAP UIDs per mailbox during this runtime.
     * Not persisted to disk to avoid UIDVALIDITY mismatch issues.
     */
    private final Map<String, Set<Long>> inMemoryProcessedUids = new ConcurrentHashMap<>();

    // ======================== Construction & Lifecycle ========================
    public EmailclawChannelRunner(
            ChannelService channelService,
            ChatService chatService,
            AgentService agentService,
            ProviderService providerService,
            ConfigManager configManager,
            ProjectService projectService) {
        this.channelService = channelService;
        this.chatService = chatService;
        this.agentService = agentService;
        this.providerService = providerService;
        this.configManager = configManager;
        this.projectService = projectService;

        Thread.startVirtualThread(
                () -> {
                    while (running.get()) {
                        int sleepSeconds = 30;
                        try {
                            ChannelInfo channel = findEmailChannel();
                            if (channel != null && channel.isEnabled()) {
                                sleepSeconds =
                                        Math.max(
                                                5,
                                                EmailclawChannelConfig.getEmailPollIntervalSeconds(
                                                        channel));
                                List<MailboxAccountConfig> mailboxes =
                                        EmailclawChannelConfig.getMailboxes(channel);
                                for (MailboxAccountConfig mailbox : mailboxes) {
                                    if (mailbox.isRunnable()) {
                                        pollMailboxInbox(channel, mailbox);
                                    } else {
                                        LOGGER.fine(
                                                () ->
                                                        "Skipping mailbox because it is not"
                                                            + " runnable (missing credentials or"
                                                            + " disabled): "
                                                                + mailbox.emailAddress());
                                    }
                                }
                            } else if (channel != null && !channel.isEnabled()) {
                                LOGGER.fine(
                                        "Emailclaw channel is globally disabled, skipping"
                                                + " polling.");
                            }
                        } catch (Throwable t) {
                            LOGGER.log(
                                    Level.WARNING, "Emailclaw multi-mailbox polling exception", t);
                        }
                        sleepQuietly(sleepSeconds * 1000L);
                    }
                });
        LOGGER.info("EmailclawChannelRunner multi-mailbox background polling supervisor started");
    }

    public void stop() {
        running.set(false);
        LOGGER.info("EmailclawChannelRunner stop has been requested");
    }

    // ======================== Channel Finding & Setup ========================
    private ChannelInfo findEmailChannel() {
        ChannelInfo channel =
                channelService.list().stream()
                        .filter(ch -> ChannelIds.EMAILCLAW.equals(ch.getId()))
                        .findFirst()
                        .orElse(null);
        if (channel != null) {
            if (EmailclawChannelConfig.normalizeEmailclawPluginConfig(channel)) {
                channelService.save();
            }
        }
        return channel;
    }

    // ======================== Polling & Ingestion ========================
    private void pollMailboxInbox(ChannelInfo channel, MailboxAccountConfig mailbox) {
        LOGGER.info(
                () -> "Polling mailbox: " + mailbox.emailAddress() + " (id=" + mailbox.id() + ")");
        List<EmailEnvelope> mails = fetchUnreadEmails(mailbox);
        if (mails.isEmpty()) {
            return;
        }
        LOGGER.info("Unread emails count for " + mailbox.emailAddress() + ": " + mails.size());

        Set<Long> processedSet =
                inMemoryProcessedUids.computeIfAbsent(
                        mailbox.id(), k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));

        for (EmailEnvelope mail : mails) {
            if (processedSet.contains(mail.uid())) {
                continue;
            }
            if (!allowedSender(channel, mailbox, mail.from())) {
                LOGGER.info("Skipping email from non-allowlisted sender: " + mail.from());
                continue;
            }
            processedSet.add(mail.uid());
            markMailAsRead(mailbox, mail.uid());
            dispatchMailHandling(channel, mailbox, mail);
        }
    }

    private void dispatchMailHandling(
            ChannelInfo channel, MailboxAccountConfig mailbox, EmailEnvelope mail) {
        Thread.startVirtualThread(
                () -> {
                    boolean acquired = false;
                    try {
                        mailProcessingPermits.acquire();
                        acquired = true;
                        safeHandleMail(channel, mailbox, mail);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOGGER.log(
                                Level.WARNING,
                                "Emailclaw processing thread interrupted: subject={0}, from={1}",
                                new Object[] {mail.subject(), mail.from()});
                    } catch (Throwable t) {
                        LOGGER.log(Level.WARNING, "Emailclaw email processing uncaught error", t);
                    } finally {
                        if (acquired) {
                            mailProcessingPermits.release();
                        }
                    }
                });
    }

    private void safeHandleMail(
            ChannelInfo channel, MailboxAccountConfig mailbox, EmailEnvelope mail) {
        try {
            handleMail(channel, mailbox, mail);
        } catch (Throwable t) {
            LOGGER.log(
                    Level.WARNING,
                    "Emailclaw handleMail exception for " + mailbox.emailAddress(),
                    t);
        }
    }

    // ======================== Core Business Processing ========================
    private void handleMail(ChannelInfo channel, MailboxAccountConfig mailbox, EmailEnvelope mail)
            throws Exception {
        String sender = normalizeSender(mail.from());
        String sessionIdFromSubject = extractTrailingSessionId(mail.subject());
        LOGGER.info(
                "Processing inbound email: from="
                        + sender
                        + ", mailbox="
                        + mailbox.emailAddress()
                        + ", sessionIdInSubject="
                        + sessionIdFromSubject);

        ChatSessionInfo session = null;
        if (isUuid(sessionIdFromSubject)) {
            session = chatService.findSession(sessionIdFromSubject);
        }

        // Rule 1: New Email Session (Subject does not contain existing SessionID)
        if (session == null) {
            AgentInfo targetAgent = null;
            if (notBlank(mailbox.targetAgentId())) {
                targetAgent = agentService.findById(mailbox.targetAgentId()).orElse(null);
            }
            if (targetAgent == null) {
                targetAgent = agentService.currentDefault();
            }

            ChatSessionInfo newSession =
                    createNewEmailSession(targetAgent, mailbox, sender, mail.subject());
            materializeMailAttachments(targetAgent, newSession, mail);
            persistBootstrapMailToSession(targetAgent, newSession, mail);
            String newSubject = truncateSubject(mail.subject()) + " " + newSession.getId();
            sendMail(mailbox, mail.replyTo(), newSubject.trim(), BOOTSTRAP_GUIDANCE_TEMPLATE);
            return;
        }

        // Rule 2: Existing Session -> Resolve Agent strictly from session.getAgentId()
        AgentInfo sessionAgent = agentService.findById(session.getAgentId()).orElse(null);
        if (sessionAgent == null) {
            LOGGER.warning(
                    "Session "
                            + session.getId()
                            + " references missing agent "
                            + session.getAgentId()
                            + ", using default agent");
            sessionAgent = agentService.currentDefault();
        }

        ProviderInfo provider = chatService.resolveEffectiveProvider(sessionAgent);
        if (provider == null || provider.allModels().isEmpty()) {
            LOGGER.warning("Provider unavailable for agent: " + sessionAgent.getId());
            return;
        }
        String modelId = chatService.resolveEffectiveModelId(sessionAgent, provider);
        if (modelId == null || modelId.isBlank()) {
            LOGGER.warning("ModelId unavailable for agent: " + sessionAgent.getId());
            return;
        }

        // Backfill session attributes
        boolean sessionUpdated = false;
        if (session.getUserId() == null || session.getUserId().isBlank()) {
            session.setUserId(sender);
            sessionUpdated = true;
        }
        if (!ChannelIds.EMAILCLAW.equals(session.getChannel())) {
            session.setChannel(ChannelIds.EMAILCLAW);
            sessionUpdated = true;
        }
        if (sessionUpdated) {
            chatService.updateSession(session);
        }

        // Rule 3: Approval Code Detection
        GovernanceService guardService = chatService.getGovernanceService();
        String emailBody = extractLatestUserContent(mail, sessionAgent, session).trim();
        if (emailBody.matches("\\d{4}")) {
            Optional<PendingApproval> pendingOpt =
                    guardService.findPendingByCode(
                            ChannelIds.EMAILCLAW, session.getId(), sender, emailBody);
            if (pendingOpt.isPresent()) {
                handleApprovalReply(
                        mailbox, mail, sessionAgent, session, sender, pendingOpt.get(), false);
                return;
            }
            pendingOpt =
                    guardService.findPendingByRememberCode(
                            ChannelIds.EMAILCLAW, session.getId(), sender, emailBody);
            if (pendingOpt.isPresent()) {
                handleApprovalReply(
                        mailbox, mail, sessionAgent, session, sender, pendingOpt.get(), true);
                return;
            }
        }

        // Rule 4: Normal User Message Dispatch
        String prompt = buildPrompt(mail, sessionAgent, session);
        List<Path> attachmentPaths = materializeMailAttachments(sessionAgent, session, mail);
        if ((prompt == null || prompt.isBlank()) && !attachmentPaths.isEmpty()) {
            prompt =
                    "Please process the attachments of this email and reply based on the attachment"
                            + " contents.";
        }

        final AgentInfo effectiveAgent = sessionAgent;
        final ChatSessionInfo effectiveSession = session;
        final MailboxAccountConfig outboundMailbox = mailbox;
        final String replySender = sender;

        chatService.sendMessage(
                effectiveAgent,
                provider,
                modelId,
                effectiveSession,
                prompt,
                attachmentPaths,
                Map.of(
                        "originalSubject",
                        mail.subject() == null ? "" : mail.subject(),
                        "originMailboxId",
                        mailbox.id()),
                new StreamCallback() {
                    @Override
                    public void onPart(ChatMessagePart part, boolean startsNew) {}

                    @Override
                    public void onCompleted(Msg message) {
                        try {
                            List<PendingApproval> pendingForSession =
                                    guardService.getPendingApprovals().stream()
                                            .filter(
                                                    p ->
                                                            effectiveSession
                                                                            .getId()
                                                                            .equals(
                                                                                    p
                                                                                            .getSessionId())
                                                                    && !p.isDelivered())
                                            .toList();
                            if (!pendingForSession.isEmpty()) {
                                for (PendingApproval approval : pendingForSession) {
                                    String subject = mail.subject() == null ? "" : mail.subject();
                                    sendApprovalRequestMail(
                                            outboundMailbox,
                                            replySender,
                                            subject.trim(),
                                            approval.getToolName(),
                                            String.valueOf(approval.getToolInput()),
                                            approval.getApprovalCode(),
                                            approval.getRememberCode());
                                    guardService.markDelivered(approval.getId());
                                }
                            } else {
                                String replyText =
                                        ChatMessageRecord.textOfParts(chatService.partsOf(message));
                                sendReply(
                                        outboundMailbox,
                                        mail,
                                        replyText,
                                        effectiveAgent,
                                        effectiveSession);
                            }
                        } catch (Exception e) {
                            LOGGER.log(
                                    Level.WARNING,
                                    "Emailclaw failed to dispatch reply/approval email",
                                    e);
                        }
                    }
                });
    }

    // ======================== Approval Handling ========================
    private void handleApprovalReply(
            MailboxAccountConfig mailbox,
            EmailEnvelope mail,
            AgentInfo agent,
            ChatSessionInfo session,
            String sender,
            PendingApproval approval,
            boolean remember) {
        String replyContent = extractLatestUserContent(mail, agent, session).trim();
        chatService.appendHistory(
                agent.getId(),
                session.getId(),
                new ChatMessageRecord(
                        ChatMessageRoles.USER,
                        List.of(ChatMessagePart.text(sender + " wrote: " + replyContent)),
                        LocalDateTime.now().toString()));

        LOGGER.info(
                "Emailclaw approval code matched: session="
                        + session.getId()
                        + ", tool="
                        + approval.getToolName());

        ToolUseBlock toolBlock =
                new ToolUseBlock(approval.getId(), approval.getToolName(), approval.getToolInput());
        List<PermissionRule> rules =
                remember
                        ? List.of(
                                new PermissionRule(
                                        approval.getToolName(),
                                        null,
                                        PermissionBehavior.ALLOW,
                                        "user_email_approved"))
                        : List.of();
        ConfirmResult confirmResult = new ConfirmResult(true, toolBlock, rules);

        chatService.resumeWithConfirmResult(
                agent.getId(),
                session.getId(),
                ChannelIds.EMAILCLAW,
                approval.getRoute() != null ? approval.getRoute() : Map.of(),
                List.of(confirmResult));

        chatService.appendHistory(
                agent.getId(),
                session.getId(),
                new ChatMessageRecord(
                        ChatMessageRoles.SYSTEM,
                        List.of(
                                ChatMessagePart.text(
                                        "User approved tool call via approval code: "
                                                + approval.getToolName())),
                        LocalDateTime.now().toString()));

        try {
            sendMail(
                    mailbox,
                    mail.replyTo(),
                    mail.subject(),
                    "Approval code received, tool call approved: " + approval.getToolName());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send approval confirmation email", e);
        }
    }

    public void sendApprovalRequestMail(
            MailboxAccountConfig mailbox,
            String to,
            String subject,
            String toolName,
            String toolInput,
            String code,
            String rememberCode) {
        if (mailbox == null || !mailbox.isRunnable()) {
            LOGGER.warning("Cannot send approval email: mailbox not configured or enabled");
            return;
        }
        if (to == null || to.isBlank()) {
            LOGGER.warning("Cannot send approval email: Missing recipient");
            return;
        }
        try {
            String mailBody = buildApprovalMailBody(toolName, toolInput, code, rememberCode);
            sendMail(mailbox, to, subject.trim(), mailBody);
            LOGGER.info("Emailclaw approval email sent: to=" + to + ", code=" + code);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send approval email", e);
        }
    }

    private String buildApprovalMailBody(
            String toolName, String toolInput, String code, String rememberCode) {
        return "Emailclaw has detected a high-risk tool call that requires your approval.\n\n"
                + "How to reply (reply directly with the corresponding 4 digits):\n"
                + "  Reply "
                + code
                + " → Approve execution for this time only\n"
                + "  Reply "
                + rememberCode
                + " → Approve and remember this decision (automatically allow similar calls next"
                + " time)\n"
                + "  Replying with anything else or not replying will be considered a rejection\n\n"
                + "Tool: "
                + (toolName == null ? "" : toolName)
                + "\n"
                + "Parameters: "
                + compactApprovalText(toolInput == null ? "" : toolInput, 1200)
                + "\n\n";
    }

    private String compactApprovalText(String text, int maxLength) {
        String value = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim() + "...";
    }

    // ======================== Sender Filtering ========================
    private boolean allowedSender(
            ChannelInfo channel, MailboxAccountConfig mailbox, String sender) {
        String normalized = normalizeSender(sender);
        if (mailbox != null && !mailbox.allowlistSenders().isEmpty()) {
            return mailbox.allowlistSenders().stream()
                    .map(this::normalizeSender)
                    .anyMatch(normalized::equals);
        }
        List<String> globalAllowlist = EmailclawChannelConfig.getEmailAllowlistSenders(channel);
        if (!globalAllowlist.isEmpty()) {
            return globalAllowlist.stream().map(this::normalizeSender).anyMatch(normalized::equals);
        }
        return false;
    }

    // ======================== Session Creation & Persistence ========================
    private ChatSessionInfo createNewEmailSession(
            AgentInfo agent, MailboxAccountConfig mailbox, String sender, String mailSubject) {
        ChatSessionInfo session = chatService.newSession(agent.getId());
        session.setChannel(ChannelIds.EMAILCLAW);
        session.setUserId(sender);
        String normalizedSubject = mailSubject == null ? "" : mailSubject.trim();
        session.setName(normalizedSubject.isBlank() ? ("Email: " + sender) : normalizedSubject);
        session.setKind(ChatSessionInfo.KIND_TASK);
        session.setDescription("originMailboxId=" + mailbox.id());

        if (this.configManager != null) {
            ai.emailclaw.emailclaw.model.ProjectInfo project =
                    new ai.emailclaw.emailclaw.model.ProjectInfo();
            project.setId(session.getId());
            project.setName(session.getName());
            String safeName = FileNameUtils.sanitizePathName(project.getName(), "Task");
            project.setBaseDirectory(
                    AppHomeConstants.HOME_RESOLVED
                                    .resolve(AppHomeConstants.PROJECTS_DIR)
                                    .toAbsolutePath()
                            + "/"
                            + safeName
                            + "-"
                            + project.getId());
            project.setCreatedAt(LocalDateTime.now().toString());

            try {
                Files.createDirectories(Path.of(project.getBaseDirectory()));
            } catch (Exception e) {
                LOGGER.log(
                        Level.WARNING,
                        "Failed to create project directory: " + project.getBaseDirectory(),
                        e);
            }
            session.setProjectId(project.getId());

            if (this.projectService != null) {
                this.projectService.save(project);
            } else {
                List<ai.emailclaw.emailclaw.model.ProjectInfo> projects =
                        new ArrayList<>(this.configManager.getProjects());
                projects.add(project);
                this.configManager.saveProjects(projects);
            }
        }

        chatService.updateSession(session);
        return session;
    }

    private void persistBootstrapMailToSession(
            AgentInfo agent, ChatSessionInfo session, EmailEnvelope mail) {
        try {
            String now = LocalDateTime.now().toString();
            chatService.appendHistory(
                    agent.getId(),
                    session.getId(),
                    new ChatMessageRecord(
                            ChatMessageRoles.USER,
                            List.of(ChatMessagePart.text(buildPrompt(mail, agent, session))),
                            now));
            chatService.touchSession(session);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to write bootstrap mail history", e);
        }
    }

    // ======================== Prompt & Attachments ========================
    private String buildPrompt(EmailEnvelope mail, AgentInfo agent, ChatSessionInfo session) {
        String subject = mail.subject() == null ? "" : mail.subject().trim();
        if (subject.replace(" ", "").endsWith(session.getId())) {
            while (subject.lastIndexOf(" ") > subject.length() - session.getId().length()) {
                subject = subject.replaceAll(" ([\\S]+$)", "$1");
            }
            subject = subject.replace(session.getId(), "");
        }
        String body = extractLatestUserContent(mail, agent, session);
        String attachmentHint = buildAttachmentHint(mail);
        return mail.from() + " wrote: " + subject + " \n " + body + attachmentHint;
    }

    private String extractLatestUserContent(
            EmailEnvelope mail, AgentInfo agent, ChatSessionInfo session) {
        String rawBody = normalizeMailBody(mail.body());
        if (rawBody.isBlank()) {
            return "";
        }
        try {
            String formatStripped = stripQuotedBlocksByFormat(rawBody);
            String deDuplicated = removeSessionHistoryLines(formatStripped, agent, session);
            String cleaned = cleanupExtractedBody(deDuplicated);
            return cleaned.isBlank() ? "" : cleaned;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to extract user content from email", e);
            return cleanupExtractedBody(rawBody);
        }
    }

    private String stripQuotedBlocksByFormat(String body) {
        String[] lines = body.split("\n", -1);
        int cutoff = lines.length;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String lower = line.trim().toLowerCase(Locale.ROOT);
            String nextLower =
                    (i + 1 < lines.length) ? lines[i + 1].trim().toLowerCase(Locale.ROOT) : "";
            if (isQuoteStartLine(line, lower, nextLower)) {
                cutoff = i;
                break;
            }
        }
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < cutoff; i++) {
            String line = lines[i];
            if (!QUOTED_PREFIX_LINE.matcher(line).matches()) {
                kept.add(line);
            }
        }
        return String.join("\n", kept);
    }

    private boolean isQuoteStartLine(String line, String lowerLine, String nextLowerLine) {
        String lineTrimmed = line == null ? "" : line.trim();
        if (lineTrimmed.isBlank()) {
            return false;
        }
        if (EN_REPLY_WROTE_LINE.matcher(lineTrimmed).matches()
                || ZH_REPLY_WROTE_LINE.matcher(lineTrimmed).matches()) {
            return true;
        }
        if (ORIGINAL_MESSAGE_LINE.matcher(lineTrimmed).matches()
                || ZH_ORIGINAL_MESSAGE_LINE.matcher(lineTrimmed).matches()) {
            return true;
        }
        if ((RFC822_HEADER_LINE.matcher(lowerLine).find()
                        || ZH_RFC822_HEADER_LINE.matcher(lineTrimmed).find())
                && (RFC822_HEADER_LINE.matcher(nextLowerLine).find()
                        || ZH_NEXT_LINE_HEADER.matcher(nextLowerLine).matches())) {
            return true;
        }
        return LONG_SEPARATOR_LINE.matcher(lineTrimmed).matches();
    }

    private static final Pattern ZH_NEXT_LINE_HEADER =
            Pattern.compile("^(?:to:|subject:|date:|sent:|发件人|主题|日期|发送时间).*");

    private String removeSessionHistoryLines(
            String text, AgentInfo agent, ChatSessionInfo session) {
        if (text == null || text.isBlank() || agent == null || session == null) {
            return text == null ? "" : text;
        }
        List<Msg> history = chatService.loadHistory(agent.getId(), session.getId());
        if (history == null || history.isEmpty()) {
            return text;
        }
        Set<String> knownHistoryLines = new LinkedHashSet<>();
        for (Msg msg : history) {
            String fullText = ChatMessageRecord.textOfParts(chatService.partsOf(msg));
            if (fullText == null || fullText.isBlank()) {
                continue;
            }
            for (String rawLine : fullText.split("\n", -1)) {
                String normalized = normalizeComparableLine(rawLine);
                if (normalized.length() >= 4) {
                    knownHistoryLines.add(normalized);
                }
            }
        }
        if (knownHistoryLines.isEmpty()) {
            return text;
        }
        String[] lines = text.split("\n", -1);
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            String norm = normalizeComparableLine(line);
            if (norm.length() >= 4 && knownHistoryLines.contains(norm)) {
                continue;
            }
            kept.add(line);
        }
        return String.join("\n", kept);
    }

    private String normalizeComparableLine(String line) {
        if (line == null) {
            return "";
        }
        String text = line.trim();
        text = text.replaceFirst("^\\s*(?:[>＞|│┃¦]+\\s*)+", "");
        text = SPACE_PATTERN.matcher(text).replaceAll(" ");
        return text.trim().toLowerCase(Locale.ROOT);
    }

    private String cleanupExtractedBody(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] lines = normalizeMailBody(text).split("\n", -1);
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (SIGNATURE_SEPARATOR_LINE.matcher(trimmed).matches()) {
                break;
            }
            kept.add(line);
        }
        return String.join("\n", kept).trim();
    }

    private String buildAttachmentHint(EmailEnvelope mail) {
        if (mail == null || mail.attachments() == null || mail.attachments().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\nAttachments:\n");
        for (MailAttachment attachment : mail.attachments()) {
            String name =
                    (attachment == null
                                    || attachment.filename() == null
                                    || attachment.filename().isBlank())
                            ? "unnamed"
                            : attachment.filename();
            int size =
                    (attachment == null || attachment.data() == null)
                            ? 0
                            : attachment.data().length;
            sb.append("- ").append(name).append(" (").append(size).append(" bytes)").append('\n');
        }
        return sb.toString().trim().isBlank() ? "" : ("\n" + sb.toString().trim());
    }

    private List<Path> materializeMailAttachments(
            AgentInfo agent, ChatSessionInfo session, EmailEnvelope mail) {
        if (mail == null || mail.attachments() == null || mail.attachments().isEmpty()) {
            return List.of();
        }
        Path targetDir;
        ai.emailclaw.emailclaw.model.ProjectInfo project = findSessionProject(session);
        if (project != null && notBlank(project.getBaseDirectory())) {
            targetDir = Path.of(project.getBaseDirectory()).resolve(ATTACHMENTS_DIR_NAME);
        } else {
            targetDir =
                    chatService
                            .sessionPath(
                                    session != null ? session.projectId() : "default",
                                    agent.getId())
                            .resolve(session != null ? session.getId() : "temp")
                            .resolve(ATTACHMENTS_DIR_NAME);
        }
        List<Path> paths = new ArrayList<>();
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to create attachments directory: " + targetDir, e);
            return List.of();
        }
        int index = 1;
        for (MailAttachment attachment : mail.attachments()) {
            if (attachment == null || attachment.data() == null || attachment.data().length == 0) {
                index++;
                continue;
            }
            if (attachment.data().length > EMAIL_ATTACHMENT_MAX_BYTES) {
                LOGGER.log(
                        Level.WARNING,
                        "Skipping oversized attachment: {0} ({1} bytes)",
                        new Object[] {attachment.filename(), attachment.data().length});
                index++;
                continue;
            }
            try {
                String filename =
                        FileNameUtils.sanitizePathName(
                                attachment.filename(), "attachment_" + index);
                Path output = uniqueAttachmentPath(targetDir, filename);
                Files.write(output, attachment.data());
                paths.add(output);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to save attachment: " + attachment.filename(), e);
            }
            index++;
        }
        return paths;
    }

    private Path uniqueAttachmentPath(Path dir, String filename) {
        Path candidate = dir.resolve(filename);
        if (!Files.exists(candidate)) {
            return candidate;
        }
        String ext = "";
        String stem = filename;
        int dot = filename.lastIndexOf('.');
        if (dot > 0) {
            stem = filename.substring(0, dot);
            ext = filename.substring(dot);
        }
        int idx = 1;
        while (true) {
            Path next = dir.resolve(stem + "_" + idx + ext);
            if (!Files.exists(next)) {
                return next;
            }
            idx++;
        }
    }

    private ai.emailclaw.emailclaw.model.ProjectInfo findSessionProject(ChatSessionInfo session) {
        if (session == null || this.configManager == null || session.getId() == null) {
            return null;
        }
        return this.configManager.getProjects().stream()
                .filter(p -> session.getId().equals(p.getId()))
                .findFirst()
                .orElse(null);
    }

    // ======================== Send Reply Email ========================
    private void sendReply(
            MailboxAccountConfig mailbox,
            EmailEnvelope mail,
            String content,
            AgentInfo agent,
            ChatSessionInfo session)
            throws IOException {
        String subject =
                (mail.subject() == null || mail.subject().isBlank())
                        ? "Reply"
                        : mail.subject()
                                .trim()
                                .replaceAll("^Re:\\s*", "")
                                .replaceAll("^RE:\\s*", "")
                                .replaceAll("^Reply[:]?\\s*", "")
                                .replaceAll("^Response[:]?\\s*", "")
                                .trim();
        List<Path> sendAttachments = new ArrayList<>();
        String finalContent = content;
        if (content != null && !content.isBlank()) {
            Matcher matcher = ATTACHMENT_TAG_PATTERN.matcher(content);
            StringBuffer cleanText = new StringBuffer();
            while (matcher.find()) {
                String pathStr = matcher.group(1).trim();
                if (!pathStr.isEmpty()) {
                    try {
                        Path file = Path.of(pathStr);
                        if (!file.isAbsolute()) {
                            Path baseDir;
                            ai.emailclaw.emailclaw.model.ProjectInfo project =
                                    findSessionProject(session);
                            if (project != null && notBlank(project.getBaseDirectory())) {
                                baseDir = Path.of(project.getBaseDirectory());
                            } else {
                                baseDir =
                                        (agent.getWorkspacePath() == null
                                                        || agent.getWorkspacePath().isBlank())
                                                ? AppHomeConstants.HOME_RESOLVED
                                                        .resolve(
                                                                AppHomeConstants
                                                                        .AGENT_WORKSPACE_DIR)
                                                        .resolve(agent.getId())
                                                : Path.of(agent.getWorkspacePath())
                                                        .toAbsolutePath()
                                                        .normalize();
                            }
                            file = baseDir.resolve(file).normalize();
                        }
                        if (Files.exists(file) && Files.isRegularFile(file)) {
                            sendAttachments.add(file);
                            matcher.appendReplacement(cleanText, "");
                        } else {
                            LOGGER.log(Level.WARNING, "Attachment file does not exist: " + pathStr);
                            matcher.appendReplacement(
                                    cleanText, String.format(ATTACHMENT_ERR_NOT_FOUND, pathStr));
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to parse attachment path: " + pathStr, e);
                        matcher.appendReplacement(
                                cleanText, String.format(ATTACHMENT_ERR_INVALID_PATH, pathStr));
                    }
                } else {
                    matcher.appendReplacement(cleanText, "");
                }
            }
            matcher.appendTail(cleanText);
            finalContent = cleanText.toString().trim();
        }
        try {
            sendMail(mailbox, mail.replyTo(), subject, finalContent, sendAttachments);
        } catch (Exception e) {
            throw new IOException("Failed to send reply email", e);
        }
    }

    public void sendMail(MailboxAccountConfig mailbox, String to, String subject, String content)
            throws IOException {
        try {
            sendMail(mailbox, to, subject, content, List.of());
        } catch (Exception e) {
            throw new IOException("Failed to send email", e);
        }
    }

    public void sendMail(
            MailboxAccountConfig mailbox,
            String to,
            String subject,
            String content,
            List<Path> attachments)
            throws Exception {
        if (mailbox == null || !mailbox.isRunnable()) {
            throw new IllegalArgumentException("Mailbox is not configured or not runnable");
        }
        LOGGER.info(
                "sendMail via " + mailbox.emailAddress() + " to " + to + ", subject=" + subject);
        Properties props = new Properties();
        props.put("mail.smtp.host", mailbox.effectiveSmtpHost());
        props.put("mail.smtp.port", String.valueOf(mailbox.effectiveSmtpPort()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", MAIL_TIMEOUT);
        props.put("mail.smtp.timeout", MAIL_TIMEOUT);
        if (mailbox.effectiveSmtpSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", String.valueOf(mailbox.effectiveSmtpPort()));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        }
        if (mailbox.effectiveSmtpStartTls()) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        String user = mailbox.emailAddress();
        String password = mailbox.emailPassword();
        Session session =
                Session.getInstance(
                        props,
                        new Authenticator() {
                            @Override
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(user, password);
                            }
                        });
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(user, FROM_NAME));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        message.setSubject(subject, "UTF-8");
        message.setSentDate(new Date());
        if (attachments == null || attachments.isEmpty()) {
            message.setText(content == null ? "" : content, "UTF-8");
        } else {
            MimeMultipart multipart = new MimeMultipart("mixed");
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(content == null ? "" : content, "UTF-8");
            multipart.addBodyPart(textPart);
            for (Path file : attachments) {
                if (file != null && Files.exists(file) && Files.isRegularFile(file)) {
                    MimeBodyPart attachPart = new MimeBodyPart();
                    attachPart.attachFile(file.toFile());
                    multipart.addBodyPart(attachPart);
                }
            }
            message.setContent(multipart);
        }
        message.saveChanges();
        Transport.send(message);
        LOGGER.info(
                "Email sent successfully: from=" + user + ", to=" + to + ", subject=" + subject);
    }

    // ======================== Fetch Emails (Jakarta Mail) ========================
    private List<EmailEnvelope> fetchUnreadEmails(MailboxAccountConfig mailbox) {
        Store store = null;
        Folder folder = null;
        try {
            Session session = createImapSession(mailbox);
            store = session.getStore("imap");
            store.connect(
                    mailbox.effectiveImapHost(),
                    mailbox.effectiveImapPort(),
                    mailbox.emailAddress(),
                    mailbox.emailPassword());
            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);

            FlagTerm unseenFlagTerm = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
            Message[] messages = folder.search(unseenFlagTerm);
            if (messages == null || messages.length == 0) {
                return List.of();
            }
            UIDFolder uidFolder = (UIDFolder) folder;
            List<EmailEnvelope> mails = new ArrayList<>();
            for (Message msg : messages) {
                long uid = uidFolder.getUID(msg);
                EmailEnvelope parsed = parseMessage(uid, msg);
                if (parsed != null) {
                    mails.add(parsed);
                }
            }
            return mails;
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING,
                    "Failed to fetch unread emails for " + mailbox.emailAddress(),
                    e);
            return List.of();
        } finally {
            closeFolder(folder);
            closeStore(store);
        }
    }

    private void markMailAsRead(MailboxAccountConfig mailbox, long uid) {
        Store store = null;
        Folder folder = null;
        try {
            Session session = createImapSession(mailbox);
            store = session.getStore("imap");
            store.connect(
                    mailbox.effectiveImapHost(),
                    mailbox.effectiveImapPort(),
                    mailbox.emailAddress(),
                    mailbox.emailPassword());
            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            if (folder instanceof UIDFolder uidFolder) {
                Message msg = uidFolder.getMessageByUID(uid);
                if (msg != null) {
                    msg.setFlag(Flags.Flag.SEEN, true);
                    LOGGER.fine(
                            () ->
                                    "Marked email as read: mailbox="
                                            + mailbox.emailAddress()
                                            + ", uid="
                                            + uid);
                }
            }
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING,
                    "Failed to mark email as read for " + mailbox.emailAddress() + ", uid=" + uid,
                    e);
        } finally {
            closeFolder(folder);
            closeStore(store);
        }
    }

    private EmailEnvelope parseMessage(long uid, Message msg) {
        try {
            String from = normalizeSender(getFirstEmail(msg.getFrom()));
            if (from.isBlank()) {
                return null;
            }
            String replyTo = normalizeSender(getFirstEmail(msg.getReplyTo()));
            if (replyTo.isBlank()) {
                replyTo = from;
            }
            String subject = msg.getSubject() == null ? "" : msg.getSubject();
            List<MailAttachment> attachments = new ArrayList<>();
            StringBuilder bodyBuilder = new StringBuilder();
            extractContent(msg.getContent(), bodyBuilder, attachments);
            return new EmailEnvelope(
                    uid,
                    from,
                    replyTo,
                    subject,
                    bodyBuilder.toString().trim(),
                    List.copyOf(attachments));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to parse email, uid=" + uid, e);
            return null;
        }
    }

    private String getFirstEmail(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        return ((InternetAddress) addresses[0]).getAddress();
    }

    private void extractContent(
            Object content, StringBuilder body, List<MailAttachment> attachments) throws Exception {
        if (content instanceof String text) {
            if (body.length() == 0) {
                body.append(text);
            }
        } else if (content instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                extractPart(mp.getBodyPart(i), body, attachments);
            }
        }
    }

    private void extractPart(Part part, StringBuilder body, List<MailAttachment> attachments)
            throws Exception {
        String disposition = part.getDisposition();
        boolean isAttachment =
                Part.ATTACHMENT.equalsIgnoreCase(disposition)
                        || (disposition != null
                                && Part.INLINE.equalsIgnoreCase(disposition)
                                && part.getFileName() != null
                                && !part.getFileName().isBlank());
        if (isAttachment) {
            String filename = part.getFileName();
            try (InputStream is = part.getInputStream()) {
                byte[] data = is.readAllBytes();
                if (data.length > 0 && data.length <= EMAIL_ATTACHMENT_MAX_BYTES) {
                    attachments.add(
                            new MailAttachment(
                                    filename == null ? "attachment" : filename,
                                    part.getContentType(),
                                    data));
                }
            }
            return;
        }
        if (part.isMimeType("text/plain")) {
            if (body.length() == 0) {
                body.append(part.getContent().toString());
            }
        } else if (part.isMimeType("text/html")) {
            if (body.length() == 0) {
                String html = part.getContent().toString();
                body.append(html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim());
            }
        } else if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                extractPart(mp.getBodyPart(i), body, attachments);
            }
        } else {
            try {
                Object subContent = part.getContent();
                if (subContent instanceof String text && body.length() == 0) {
                    body.append(text);
                } else if (subContent instanceof Multipart mp) {
                    for (int i = 0; i < mp.getCount(); i++) {
                        extractPart(mp.getBodyPart(i), body, attachments);
                    }
                }
            } catch (Exception ignore) {
            }
        }
    }

    private Session createImapSession(MailboxAccountConfig mailbox) {
        Properties props = new Properties();
        props.put("mail.imap.host", mailbox.effectiveImapHost());
        props.put("mail.imap.port", String.valueOf(mailbox.effectiveImapPort()));
        props.put("mail.imap.connectiontimeout", MAIL_TIMEOUT);
        props.put("mail.imap.timeout", MAIL_TIMEOUT);
        if (mailbox.effectiveImapSsl()) {
            props.put("mail.imap.ssl.enable", "true");
        }
        if (mailbox.effectiveImapStartTls()) {
            props.put("mail.imap.starttls.enable", "true");
        }
        return Session.getInstance(props);
    }

    private void closeFolder(Folder folder) {
        if (folder != null) {
            try {
                folder.close(false);
            } catch (MessagingException ignore) {
            }
        }
    }

    private void closeStore(Store store) {
        if (store != null) {
            try {
                store.close();
            } catch (MessagingException ignore) {
            }
        }
    }

    private String normalizeSender(String sender) {
        if (sender == null || sender.isBlank()) {
            return "";
        }
        Matcher matcher = EMAIL_PATTERN.matcher(sender);
        if (matcher.find()) {
            return matcher.group(1).trim().toLowerCase(Locale.ROOT);
        }
        return sender.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMailBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        return body.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String truncateSubject(String subject) {
        if (subject == null || subject.trim().isEmpty()) {
            return "";
        }
        return subject.trim().length() > 165 ? subject.trim().substring(0, 165) : subject.trim();
    }

    private String extractTrailingSessionId(String subject) {
        String text = subject == null ? "" : subject.trim().replace(" ", "");
        if (text.length() < 36) {
            return "";
        }
        return text.substring(text.length() - 36);
    }

    private boolean isUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ======================== Inner Helper Types ========================
    record EmailEnvelope(
            long uid,
            String from,
            String replyTo,
            String subject,
            String body,
            List<MailAttachment> attachments) {}

    record MailAttachment(String filename, String contentType, byte[] data) {}
}

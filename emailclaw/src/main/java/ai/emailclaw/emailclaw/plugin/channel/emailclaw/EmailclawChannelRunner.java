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
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emailclaw background polling executor.
 * Emailclaw's channel enable/disable mechanism is fully inherited from agentscope-java, implementation method:
 * Background virtual threads periodically check configuration, taking effect in real-time without restart.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Periodically fetch unread emails from mailbox (based on Jakarta Mail)</li>
 *   <li>Filter emails based on allowed sender whitelist</li>
 *   <li>Convert email content to prompt and hand it over to the current default Agent</li>
 *   <li>Send model response back to the other party via email (based on Jakarta Mail)</li>
 * </ul>
 *
 * <p>Implementation strategy:
 * <ul>
 *   <li>Use Jakarta Mail (Eclipse Angus 2.0.5) as the email protocol layer, replacing the original Socket implementation</li>
 *   <li>Use IMAP UID to record the last processing position to avoid duplicate consumption</li>
 *   <li>Auto-complete server parameters for common email domains</li>
 * </ul>
 *
 * Complete Emailclaw approval event flow
 * ① User sends email -> EmailclawRunner.pollInbox()
 * ② -> ChatService.sendMessage() -> reactAgent.streamEvents()
 * ③ ReActAgent acting -> PermissionEngine -> ASK
 * ④ -> emit RequireUserConfirmEvent + RequestStopEvent
 * ⑤ ChatService detect RequireUserConfirmEvent
 *    ├─ Extract pending ToolUseBlock
 *    ├─ Build approval email (tool name + parameters + 4-digit code)
 *    ├─ Send to user via Jakarta Mail
 *    └─ Block and wait (CompletableFuture)
 * ⑥ User replies to email (approval code)
 *    ├─ EmailclawRunner.handleApprovalReply() -> complete future
 *    └─ ChatService receives APPROVED/REJECTED
 * ⑦ Construct ConfirmResult (including "remember" rules)
 * ⑧ Call agent.call(List.of(resumeMsg)) to resume
 * ⑨ ReActAgent.applyConfirmResults() -> Mark tool ALLOWED
 *    -> Continue ReAct loop -> Execute tool -> Send result email to user
 */
public class EmailclawChannelRunner {

    private static final Logger LOGGER = Logger.getLogger(EmailclawChannelRunner.class.getName());

    // ======================== Constants Area ========================
    /**
     * Maximum concurrent mail processing threads
     */
    private static final int MAIL_PROCESSING_MAX_CONCURRENCY = 4;

    /**
     * Attachment size limit: 10MB
     */
    private static final long EMAIL_ATTACHMENT_MAX_BYTES = 10L * 1024L * 1024L;

    /**
     * Email address extraction regex
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+)");

    /**
     * Quoted prefix line detection: > ＞ | │ ┃ ¦
     */
    private static final Pattern QUOTED_PREFIX_LINE =
            Pattern.compile("^\\s{0,6}(?:[>＞]|\\|\\s|│|┃|¦).*$");

    /**
     * English reply marker: On ... wrote:
     */
    private static final Pattern EN_REPLY_WROTE_LINE =
            Pattern.compile("(?i)^\\s*on\\s+.+\\s+wrote\\s*:\\s*$");

    /**
     * Chinese reply marker
     */
    private static final Pattern ZH_REPLY_WROTE_LINE =
            Pattern.compile("(?i)^\\s*on\\s+.+\\s+wrote\\s*:\\s*$");

    /**
     * English original message marker: -----Original Message----- / -----Forwarded Message-----
     */
    private static final Pattern ORIGINAL_MESSAGE_LINE =
            Pattern.compile(
                    "(?i)^\\s*-{2,}\\s*(?:original message|forwarded message)\\s*-{2,}\\s*$");

    /**
     * Chinese original message marker
     */
    private static final Pattern ZH_ORIGINAL_MESSAGE_LINE =
            Pattern.compile("^\\s*-{2,}\\s*(?:原始邮件|转发邮件)\\s*-{2,}\\s*$");

    /**
     * RFC822 header start line (English)
     */
    private static final Pattern RFC822_HEADER_LINE =
            Pattern.compile("(?i)^\\s*(?:from|sent|to|subject|cc|date|reply-to)\\s*:");

    /**
     * RFC822 header start line (Chinese)
     */
    private static final Pattern ZH_RFC822_HEADER_LINE =
            Pattern.compile("^\\s*(?:发件人|发送时间|收件人|主题|抄送|日期|答复至)\\s*[：:]");

    /**
     * Next line header (Chinese)
     */
    private static final Pattern ZH_NEXT_LINE_HEADER =
            Pattern.compile("^(?:to:|subject:|date:|sent:|发件人|主题|日期|发送时间).*");

    /**
     * Signature separator line: --
     */
    private static final Pattern SIGNATURE_SEPARATOR_LINE = Pattern.compile("^\\s*--\\s*$");

    /**
     * Long separator line: continuous - _ = (at least 6)
     */
    private static final Pattern LONG_SEPARATOR_LINE = Pattern.compile("^\\s*[-_=]{6,}\\s*$");

    /**
     * Used for multiple spaces folding
     */
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");

    /**
     * Attachment tag pattern (used to detect attachment tags in model replies)
     */
    private static final Pattern ATTACHMENT_TAG_PATTERN =
            Pattern.compile("^" + MessageMarkupTags.ATTACHMENT_PATTERN + "$", Pattern.MULTILINE);

    /**
     * Guide reply email template
     */
    private static final String BOOTSTRAP_GUIDANCE_TEMPLATE =
            "For security reasons, the system only processes emails with valid TaskId in the"
                + " subject line. This email has now been updated with the newly created TaskId."
                + " Please reply to this email, and the system will immediately start working for"
                + " you.";

    /**
     * Attachment error: file not found
     */
    private static final String ATTACHMENT_ERR_NOT_FOUND = "[Attachment Error: File not found: %s]";

    /**
     * Attachment error: invalid path
     */
    private static final String ATTACHMENT_ERR_INVALID_PATH =
            "[Attachment Error: Invalid path: %s]";

    /**
     * Attachment directory name
     */
    private static final String ATTACHMENTS_DIR_NAME = WorkspacePaths.ATTACHMENTS_DIR;

    /**
     * Default attachment MIME type
     */
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    /**
     * IMAP connection timeout (ms)
     */
    private static final String MAIL_TIMEOUT = "15000";

    /**
     * Sender display name
     */
    private static final String FROM_NAME = "Emailclaw";

    // ======================== Instance Fields ========================
    private final ChannelService channelService;

    private final ChatService chatService;

    private final AgentService agentService;

    private final ProviderService providerService;

    private final ai.emailclaw.emailclaw.storage.ConfigManager configManager;

    private final ProjectService projectService;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private final Semaphore mailProcessingPermits = new Semaphore(MAIL_PROCESSING_MAX_CONCURRENCY);

    // Use GovernanceService to manage pending approval requests, no longer use memory blocking Map
    // ======================== Construction & Lifecycle ========================
    /**
     * Constructs and starts the background polling thread.
     *
     * <p>The thread continuously reads the latest Channel configuration, supporting immediate effect after the user modifies the configuration in the UI.
     */
    public EmailclawChannelRunner(
            ChannelService channelService,
            ChatService chatService,
            AgentService agentService,
            ProviderService providerService,
            ai.emailclaw.emailclaw.storage.ConfigManager configManager,
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
                            if (channel != null) {
                                sleepSeconds =
                                        Math.max(
                                                5,
                                                EmailclawChannelConfig.getEmailPollIntervalSeconds(
                                                        channel));
                                if (isConfiguredAndEnabled(channel)) {
                                    pollInbox(channel);
                                }
                            }
                        } catch (Throwable t) {
                            LOGGER.log(Level.WARNING, "Emailclaw polling exception", t);
                        }
                        sleepQuietly(sleepSeconds * 1000L);
                    }
                });
        LOGGER.info("EmailclawRunner background polling thread has started");
    }

    /**
     * Requests to stop the background polling thread.
     */
    public void stop() {
        running.set(false);
        LOGGER.info("EmailclawRunner stop has been requested");
    }

    // ======================== ToolGuard Approval Process ========================
    /**
     * Sends an approval request email to the user (non-blocking).
     * Called by the onCompleted callback of handleMail when a pending approval request is detected.
     */
    public void sendApprovalRequestMail(
            ChannelInfo channel,
            String to,
            String subject,
            String toolName,
            String toolInput,
            String code,
            String rememberCode) {
        if (channel == null || !isConfiguredAndEnabled(channel)) {
            LOGGER.warning(
                    "Emailclaw approval email sending failed: Emailclaw is not enabled or"
                            + " configuration is incomplete");
            return;
        }
        if (to == null || to.isBlank()) {
            LOGGER.warning("Emailclaw approval email sending failed: Missing recipient");
            return;
        }
        try {
            String mailBody = buildApprovalMailBody(toolName, toolInput, code, rememberCode);
            sendMail(channel, to, subject.trim(), mailBody);
            LOGGER.info(
                    "Emailclaw approval email sent successfully: to="
                            + to
                            + ", oneTimeCode="
                            + code
                            + ", rememberCode="
                            + rememberCode);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Emailclaw failed to send approval email", e);
        }
    }

    /**
     * Builds the body of the approval email.
     */
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

    // ======================== Channel Configuration ========================
    /**
     * Finds the Emailclaw configuration object in the channel list.
     */
    private ChannelInfo findEmailChannel() {
        ChannelInfo channel =
                channelService.list().stream()
                        .filter(ch -> ChannelIds.EMAILCLAW.equals(ch.getId()))
                        .findFirst()
                        .orElse(null);
        if (EmailclawChannelConfig.normalizeEmailclawPluginConfig(channel)) {
            channelService.save();
        }
        if (EmailclawChannelConfig.isSysEmailMode(channel)
                && notBlank(EmailclawChannelConfig.getRegistrantEmail(channel))
                && notBlank(EmailclawChannelConfig.getOneTimePassword(channel))) {
            String sysEmail =
                    OneTimePasswordAuth.oneTimePasswordAuth(
                            channel,
                            EmailclawChannelConfig.getRegistrantEmail(channel),
                            EmailclawChannelConfig.getOneTimePassword(channel));
            if (sysEmail != null) {
                channelService.save();
            }
        }
        return channel;
    }

    /**
     * Determines whether Emailclaw has reached a 'runnable' state.
     */
    private boolean isConfiguredAndEnabled(ChannelInfo channel) {
        MailRuntimeConfig runtime = resolveRuntimeConfig(channel);
        return channel.isEnabled()
                && notBlank(EmailclawChannelConfig.getEmailAddress(channel))
                && notBlank(EmailclawChannelConfig.getEmailPassword(channel))
                && notBlank(runtime.imapHost())
                && notBlank(runtime.smtpHost());
    }

    // ======================== Core Polling ========================
    /**
     * Executes a single inbox poll and processes new emails.
     */
    private void pollInbox(ChannelInfo channel) {
        LOGGER.info("Emailclaw polling mailbox " + EmailclawChannelConfig.getEmailAddress(channel));
        List<EmailEnvelope> mails = fetchUnreadEmails(channel);
        LOGGER.info("Unread emails count: " + mails.size());
        if (mails.isEmpty()) {
            return;
        }
        for (EmailEnvelope mail : mails) {
            // Only process allowedSender, i.e., do not process non-allowedSender
            if (!allowedSender(channel, mail.from())) {
                continue;
            }
            markMailAsRead(channel, mail.uid());
            ChannelInfo smtpSnapshot = snapshotChannelForMailProcessing(channel);
            dispatchMailHandling(smtpSnapshot, mail);
        }
    }

    /**
     * Dispatches a single email processing task.
     */
    private void dispatchMailHandling(ChannelInfo channelSnapshot, EmailEnvelope mail) {
        LOGGER.info(
                "dispatchMailHandling: channel==="
                        + channelSnapshot.getId()
                        + channelSnapshot.getName());
        Thread.startVirtualThread(
                () -> {
                    boolean acquired = false;
                    try {
                        mailProcessingPermits.acquire();
                        acquired = true;
                        safeHandleMail(channelSnapshot, mail);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        LOGGER.log(
                                Level.WARNING,
                                "Emailclaw email processing thread interrupted: subject={0},"
                                        + " from={1}",
                                new Object[] {
                                    mail == null || mail.subject() == null ? "" : mail.subject(),
                                    mail == null || mail.from() == null ? "" : mail.from()
                                });
                    } catch (Throwable t) {
                        LOGGER.log(Level.WARNING, "Emailclaw email processing thread exception", t);
                    } finally {
                        if (acquired) {
                            mailProcessingPermits.release();
                        }
                    }
                });
    }

    /**
     * Outermost fallback for single email processing.
     */
    private void safeHandleMail(ChannelInfo channelSnapshot, EmailEnvelope mail) {
        LOGGER.info(
                "safeHandleMail: channel===" + channelSnapshot.getId() + channelSnapshot.getName());
        try {
            handleMail(channelSnapshot, mail);
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Emailclaw handleMail uncaught exception (isolated)", t);
        }
    }

    /**
     * Copy sending-related configuration in the current channel to avoid affecting ongoing mail tasks during concurrent UI thread modifications.
     */
    private ChannelInfo snapshotChannelForMailProcessing(ChannelInfo channel) {
        LOGGER.info(
                "snapshotChannelForMailProcessing: channel==="
                        + channel.getId()
                        + channel.getName());
        ChannelInfo copied = new ChannelInfo();
        copied.setId(channel.getId());
        copied.setName(channel.getName());
        copied.setBuiltIn(channel.isBuiltIn());
        copied.setEnabled(channel.isEnabled());
        copied.setPluginConfig(EmailclawChannelConfig.copyPluginConfig(channel));
        return copied;
    }

    // ======================== Sender Filtering ========================
    /**
     * Whitelist is not empty and requires a whitelist hit.
     */
    private boolean allowedSender(ChannelInfo channel, String sender) {
        boolean allowed = false;
        if (EmailclawChannelConfig.getEmailAllowlistSenders(channel).isEmpty()) {
            allowed = false;
        } else {
            String normalized = normalizeSender(sender);
            allowed =
                    EmailclawChannelConfig.getEmailAllowlistSenders(channel).stream()
                            .map(this::normalizeSender)
                            .anyMatch(normalized::equals);
        }
        if (allowed) LOGGER.info(allowed + "===allowedSender: sender===" + sender);
        return allowed;
    }

    // ======================== Mark as Read ========================
    /**
     * Mark specified UID email as read (based on Jakarta Mail).
     */
    private void markMailAsRead(ChannelInfo channel, long uid) {
        LOGGER.info("markMailAsRead: uid===" + uid);
        MailRuntimeConfig runtime = resolveRuntimeConfig(channel);
        Store store = null;
        Folder folder = null;
        try {
            Session session = createImapSession(runtime);
            store = session.getStore("imap");
            store.connect(
                    runtime.imapHost(),
                    runtime.imapPort(),
                    EmailclawChannelConfig.getEmailAddress(channel),
                    EmailclawChannelConfig.getEmailPassword(channel));
            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            if (folder instanceof UIDFolder uidFolder) {
                Message msg = uidFolder.getMessageByUID(uid);
                if (msg != null) {
                    msg.setFlag(Flags.Flag.SEEN, true);
                    LOGGER.fine("Email marked as read, uid=" + uid);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Emailclaw failed to mark email as read, uid=" + uid, e);
        } finally {
            closeFolder(folder);
            closeStore(store);
        }
    }

    // ======================== Core Business Processing ========================
    /**
     * Core business process for handling a single email.
     */
    private void handleMail(ChannelInfo channel, EmailEnvelope mail) {
        LOGGER.info("Emailclaw single email processing started");
        try {
            AgentInfo agent = agentService.currentDefault();
            ProviderInfo provider = chatService.resolveEffectiveProvider(agent);
            if (provider == null || provider.allModels().isEmpty()) {
                return;
            }
            String modelId = chatService.resolveEffectiveModelId(agent, provider);
            if (modelId == null || modelId.isBlank()) {
                return;
            }
            String sender = normalizeSender(mail.from());
            String sessionIdFromSubject = extractTrailingSessionId(mail.subject());
            LOGGER.info("sessionIdFromSubject=" + sessionIdFromSubject);
            Optional<ChatSessionInfo> optSession =
                    findSessionBySessionId(agent, sessionIdFromSubject);
            ChatSessionInfo session = optSession.orElse(null);
            // Rule 1: Subject does not end with a valid SessionID or historical session not matched
            // -> Create new session and return bootstrap email
            if (session == null) {
                ChatSessionInfo newSession = createNewEmailSession(agent, sender, mail.subject());
                materializeMailAttachments(agent, newSession, mail);
                persistBootstrapMailToSession(agent, newSession, mail);
                String newSubject = truncateSubject(mail.subject()) + " " + newSession.getId();
                sendMail(channel, mail.replyTo(), newSubject.trim(), BOOTSTRAP_GUIDANCE_TEMPLATE);
                return;
            }
            // Historical session may lack userId/channel, backfill
            boolean sessionUpdated = false;
            if (session.getUserId() == null || session.getUserId().isBlank()) {
                LOGGER.info(
                        "Emailclaw session missing userId, backfilling with sender: session="
                                + session.getId());
                session.setUserId(sender);
                sessionUpdated = true;
            }
            if (session.getChannel() == null
                    || session.getChannel().isBlank()
                    || !ChannelIds.EMAILCLAW.equals(session.getChannel())) {
                LOGGER.info(
                        "Emailclaw session channel corrected to EMAILCLAW: session="
                                + session.getId());
                session.setChannel(ChannelIds.EMAILCLAW);
                sessionUpdated = true;
            }
            if (sessionUpdated) {
                chatService.updateSession(session);
            }
            LOGGER.fine(
                    "Processing session: userId="
                            + session.getUserId()
                            + ", channel="
                            + session.getChannel());
            // Rule 2: Approval code reply detection - Supports double codes (1234 for this time
            // only, 5678 to approve and remember)
            GovernanceService guardService = chatService.getGovernanceService();
            String emailBody = extractLatestUserContent(mail, agent, session).trim();
            if (emailBody.matches("\\d{4}")) {
                // Check for 'this time only' code first
                Optional<PendingApproval> pendingOpt =
                        guardService.findPendingByCode(
                                ChannelIds.EMAILCLAW, session.getId(), sender, emailBody);
                if (pendingOpt.isPresent()) {
                    handleApprovalReply(
                            channel, mail, agent, session, sender, pendingOpt.get(), false);
                    return;
                }
                // Then check 'remember' code
                pendingOpt =
                        guardService.findPendingByRememberCode(
                                ChannelIds.EMAILCLAW, session.getId(), sender, emailBody);
                if (pendingOpt.isPresent()) {
                    handleApprovalReply(
                            channel, mail, agent, session, sender, pendingOpt.get(), true);
                    return;
                }
            }
            // Rule 3: Normal message -> Build prompt to call LLM
            String prompt = buildPrompt(mail, agent, session);
            List<Path> attachmentPaths = materializeMailAttachments(agent, session, mail);
            if ((prompt == null || prompt.isBlank()) && !attachmentPaths.isEmpty()) {
                prompt =
                        "Please process the attachments of this email and reply based on the"
                                + " attachment contents.";
            }
            // ── Current channel / mail / sender for StreamCallback closure use ──
            final ChannelInfo channelSnapshot = channel;
            final String replySender = sender;
            chatService.sendMessage(
                    agent,
                    provider,
                    modelId,
                    session,
                    prompt,
                    attachmentPaths,
                    Map.of("originalSubject", mail.subject() == null ? "" : mail.subject()),
                    new StreamCallback() {

                        @Override
                        public void onPart(ChatMessagePart part, boolean startsNew) {}

                        @Override
                        public void onCompleted(Msg message) {
                            try {
                                // Check if there are pending approval requests triggered by
                                // PermissionEngine that need to send emails
                                List<PendingApproval> pendingForSession =
                                        guardService.getPendingApprovals().stream()
                                                .filter(
                                                        p ->
                                                                session.getId()
                                                                                .equals(
                                                                                        p
                                                                                                .getSessionId())
                                                                        && !p.isDelivered())
                                                .toList();
                                if (!pendingForSession.isEmpty()) {
                                    for (PendingApproval approval : pendingForSession) {
                                        String subject =
                                                mail.subject() == null ? "" : mail.subject();
                                        sendApprovalRequestMail(
                                                channelSnapshot,
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
                                            ai.emailclaw.emailclaw.model.ChatMessageRecord
                                                    .textOfParts(chatService.partsOf(message));
                                    sendReply(channelSnapshot, mail, replyText, agent, session);
                                }
                            } catch (Exception e) {
                                LOGGER.log(
                                        Level.WARNING,
                                        "Emailclaw failed to send reply/approval email",
                                        e);
                            }
                        }
                    });
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "Emailclaw handleMail failed", e);
        }
    }

    /**
     * Handle approval code reply.
     */
    private void handleApprovalReply(
            ChannelInfo channel,
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
                "Emailclaw approval code matched successfully: session="
                        + session.getId()
                        + ", tool="
                        + approval.getToolName()
                        + ", remember="
                        + remember);
        ToolUseBlock toolBlock =
                new ToolUseBlock(approval.getId(), approval.getToolName(), approval.getToolInput());
        // Only add ALLOW rule when remember=true ("remember this decision")
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
                                        "User has approved tool call via approval code: "
                                                + approval.getToolName())),
                        LocalDateTime.now().toString()));
        try {
            sendMail(
                    channel,
                    mail.replyTo(),
                    mail.subject(),
                    "Approval code received, tool call approved: " + approval.getToolName());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send approval confirmation email", e);
        }
    }

    /**
     * Truncate subject to first 165 characters.
     */
    private String truncateSubject(String subject) {
        if (subject == null || subject.trim().isEmpty()) {
            return "";
        }
        return subject.trim().length() > 165 ? subject.trim().substring(0, 165) : subject.trim();
    }

    // ======================== Session Management ========================
    /**
     * Find historical session under specified Agent by sessionId.
     */
    private Optional<ChatSessionInfo> findSessionBySessionId(AgentInfo agent, String sessionId) {
        if (!isUuid(sessionId)) {
            return Optional.empty();
        }
        return chatService.sessions(agent.getId()).stream()
                .filter(item -> sessionId.equals(item.getId()))
                .findFirst();
    }

    /**
     * Create Emailclaw exclusive session.
     */
    private ChatSessionInfo createNewEmailSession(
            AgentInfo agent, String sender, String mailSubject) {
        ChatSessionInfo session = chatService.newSession(agent.getId());
        session.setChannel(ChannelIds.EMAILCLAW);
        session.setUserId(sender);
        String normalizedSubject = mailSubject == null ? "" : mailSubject.trim();
        session.setName(normalizedSubject.isBlank() ? ("Email: " + sender) : normalizedSubject);
        session.setKind(ChatSessionInfo.KIND_TASK);

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
            project.setCreatedAt(java.time.LocalDateTime.now().toString());

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
                // Persist through ProjectService so registered listeners (e.g. UI refresh)
                // are notified of the new project.
                this.projectService.save(project);
            } else {
                java.util.List<ai.emailclaw.emailclaw.model.ProjectInfo> projects =
                        new java.util.ArrayList<>(this.configManager.getProjects());
                projects.add(project);
                this.configManager.saveProjects(projects);
            }
        }

        chatService.updateSession(session);
        return session;
    }

    /**
     * When a new session is created, write the original email to history.
     */
    private void persistBootstrapMailToSession(
            AgentInfo agent, ChatSessionInfo session, EmailEnvelope mail) {
        LOGGER.info("persistBootstrapMailToSession from " + mail.from());
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
            LOGGER.log(
                    Level.WARNING,
                    "Emailclaw failed to write new session initialization history",
                    e);
        }
    }

    /**
     * Extract the last 36 characters of the subject as a candidate sessionId.
     */
    private String extractTrailingSessionId(String subject) {
        String text = subject == null ? "" : subject.trim().replace(" ", "");
        if (text.length() < 36) {
            return "";
        }
        return text.substring(text.length() - 36);
    }

    /**
     * Check if a string is a valid UUID.
     */
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

    // ======================== Prompt Build ========================
    /**
     * Assemble the final prompt for the LLM.
     */
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

    /**
     * Extract the "new input this time" from the email body.
     *
     * <p>Adopts a two-layer strategy: first trim quoted blocks by format, then deduplicate based on historical content.
     */
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
            LOGGER.log(Level.WARNING, "Emailclaw failed to extract new content from this email", e);
            return cleanupExtractedBody(rawBody);
        }
    }

    // ======================== Quote Block Extraction ========================
    /**
     * Truncate according to common reply formats of email clients.
     */
    private String stripQuotedBlocksByFormat(String body) {
        String[] lines = body.split("\n", -1);
        int cutoff = lines.length;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String lower = line.trim().toLowerCase(Locale.ROOT);
            String nextLower = "";
            for (int j = i + 1; j < lines.length; j++) {
                String tmp = lines[j].trim().toLowerCase(Locale.ROOT);
                if (!tmp.isEmpty()) {
                    nextLower = tmp;
                    break;
                }
            }
            if (isQuoteStartLine(line, lower, nextLower)) {
                cutoff = i;
                break;
            }
        }
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < cutoff; i++) {
            String line = lines[i];
            if (QUOTED_PREFIX_LINE.matcher(line).matches()) {
                continue;
            }
            kept.add(line);
        }
        return String.join("\n", kept);
    }

    /**
     * Determine if a line represents the "start of a quote block".
     */
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
        if (lineTrimmed.toLowerCase(Locale.ROOT).startsWith("begin forwarded message")) {
            return true;
        }
        if (LONG_SEPARATOR_LINE.matcher(lineTrimmed).matches()) {
            if (ZH_NEXT_LINE_HEADER.matcher(nextLowerLine).matches()) {
                return true;
            }
        }
        if (RFC822_HEADER_LINE.matcher(lineTrimmed).matches()
                || ZH_RFC822_HEADER_LINE.matcher(lineTrimmed).matches()) {
            if (ZH_NEXT_LINE_HEADER.matcher(nextLowerLine).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove residual old content based on session history.
     */
    private String removeSessionHistoryLines(
            String text, AgentInfo agent, ChatSessionInfo session) {
        Set<String> historyLines = collectNormalizedHistoryLines(agent, session);
        if (historyLines.isEmpty()) {
            return text;
        }
        String[] lines = text.split("\n", -1);
        int cutoff = lines.length;
        int streak = 0;
        for (int i = 0; i < lines.length; i++) {
            String normalized = normalizeComparableLine(lines[i]);
            boolean matched = normalized.length() >= 12 && historyLines.contains(normalized);
            if (matched) {
                streak++;
                if (streak >= 2) {
                    cutoff = i - streak + 1;
                    break;
                }
            } else if (!normalized.isBlank()) {
                streak = 0;
            }
        }
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < cutoff; i++) {
            String line = lines[i];
            String normalized = normalizeComparableLine(line);
            if (normalized.length() >= 24 && historyLines.contains(normalized)) {
                continue;
            }
            kept.add(line);
        }
        return String.join("\n", kept);
    }

    /**
     * Collect "comparable lines" from the session history.
     */
    private Set<String> collectNormalizedHistoryLines(AgentInfo agent, ChatSessionInfo session) {
        try {
            Set<String> result = new LinkedHashSet<>();
            List<Msg> records = chatService.loadHistory(agent.getId(), session.getId());
            for (var record : records) {
                String content =
                        ai.emailclaw.emailclaw.model.ChatMessageRecord.textOfParts(
                                chatService.partsOf(record));
                if (content == null || content.isBlank()) {
                    continue;
                }
                for (String line : normalizeMailBody(content).split("\n")) {
                    String normalized = normalizeComparableLine(line);
                    if (normalized.length() >= 12) {
                        result.add(normalized);
                    }
                }
            }
            return result;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to read session history line index (ignored)", e);
            return Set.of();
        }
    }

    // ======================== Text Tools ========================
    /**
     * Normalize body line breaks and invisible whitespace.
     */
    private String normalizeMailBody(String body) {
        if (body == null) {
            return "";
        }
        String text = body.replace("\n", "\n").replace('\r', '\n');
        text = text.replace('\u00a0', ' ');
        return text.trim();
    }

    /**
     * Normalize a single line into a comparison key (remove quote prefixes, collapse whitespace, lowercase).
     */
    private String normalizeComparableLine(String line) {
        if (line == null) {
            return "";
        }
        String text = line.trim();
        text = text.replaceFirst("^\\s*(?:[>＞|│┃¦]+\\s*)+", "");
        text = SPACE_PATTERN.matcher(text).replaceAll(" ");
        return text.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Final cleanup: remove empty lines, remove content after signature separator.
     */
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
        String merged = String.join("\n", kept).trim();
        return merged.isBlank() ? "" : merged;
    }

    /**
     * Build attachment hint text.
     */
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

    /**
     * Write email attachments to disk.
     */
    private List<Path> materializeMailAttachments(
            AgentInfo agent, ChatSessionInfo session, EmailEnvelope mail) {
        LOGGER.info("materializeMailAttachments from: " + mail.from());
        if (mail == null || mail.attachments() == null || mail.attachments().isEmpty()) {
            return List.of();
        }
        Path targetDir;
        ai.emailclaw.emailclaw.model.ProjectInfo project = findSessionProject(session);
        if (project != null
                && project.getBaseDirectory() != null
                && !project.getBaseDirectory().isBlank()) {
            targetDir = Path.of(project.getBaseDirectory()).resolve(ATTACHMENTS_DIR_NAME);
        } else {
            targetDir =
                    chatService
                            .sessionPath(
                                    session != null ? session.projectId() : "default",
                                    agent.getId())
                            .resolve(session.getId())
                            .resolve(ATTACHMENTS_DIR_NAME);
        }
        List<Path> paths = new ArrayList<>();
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to create attachment directory: " + targetDir, e);
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
                // Clean up invalid characters in the attachment filename.
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

    /**
     * Generate unique attachment path.
     */
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

    /**
     * Find the task project bound to the given session (project id equals session id for email
     * tasks). Returns null when no matching project record exists.
     */
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
    /**
     * Send model result as a reply email to the user.
     */
    private void sendReply(
            ChannelInfo channel,
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
                        LOGGER.info("Sending attachment: " + file);
                        if (!file.isAbsolute()) {
                            // Resolve relative paths against the task project directory first;
                            // fall back to the legacy agent workspace when no project exists.
                            Path baseDir;
                            ai.emailclaw.emailclaw.model.ProjectInfo project =
                                    findSessionProject(session);
                            if (project != null
                                    && project.getBaseDirectory() != null
                                    && !project.getBaseDirectory().isBlank()) {
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
            sendMail(channel, mail.replyTo(), subject, finalContent, sendAttachments);
        } catch (Exception e) {
            throw new IOException("Failed to send reply email", e);
        }
    }

    // ======================== Send Email (Jakarta Mail) ========================
    /**
     * Sends a plain text email (no attachments).
     */
    void sendMail(ChannelInfo channel, String to, String subject, String content)
            throws IOException {
        try {
            sendMail(channel, to, subject, content, List.of());
        } catch (Exception e) {
            throw new IOException("Failed to send email", e);
        }
    }

    /**
     * Sends an email (supports attachments), based on Jakarta Mail Transport.
     */
    private void sendMail(
            ChannelInfo channel, String to, String subject, String content, List<Path> attachments)
            throws Exception {
        LOGGER.info("sendMail to " + to + ", subject=" + subject);
        MailRuntimeConfig runtime = resolveRuntimeConfig(channel);
        Properties props = new Properties();
        props.put("mail.smtp.host", runtime.smtpHost());
        props.put("mail.smtp.port", String.valueOf(runtime.smtpPort()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", MAIL_TIMEOUT);
        props.put("mail.smtp.timeout", MAIL_TIMEOUT);
        if (runtime.smtpSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", String.valueOf(runtime.smtpPort()));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        }
        if (runtime.smtpStartTls()) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        String user = EmailclawChannelConfig.getEmailAddress(channel);
        String password = EmailclawChannelConfig.getEmailPassword(channel);
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
                if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
                    continue;
                }
                MimeBodyPart attachPart = new MimeBodyPart();
                attachPart.attachFile(file.toFile());
                multipart.addBodyPart(attachPart);
            }
            message.setContent(multipart);
        }
        message.saveChanges();
        Transport.send(message);
        LOGGER.info("Email sent successfully: to=" + to + ", subject=" + subject);
    }

    // ======================== Receive Email (Jakarta Mail) ========================
    /**
     * Fetches and parses unread emails, based on Jakarta Mail IMAP.
     */
    private List<EmailEnvelope> fetchUnreadEmails(ChannelInfo channel) {
        MailRuntimeConfig runtime = resolveRuntimeConfig(channel);
        Store store = null;
        Folder folder = null;
        try {
            Session session = createImapSession(runtime);
            store = session.getStore("imap");
            store.connect(
                    runtime.imapHost(),
                    runtime.imapPort(),
                    EmailclawChannelConfig.getEmailAddress(channel),
                    EmailclawChannelConfig.getEmailPassword(channel));
            folder = store.getFolder("INBOX");
            folder.open(Folder.READ_WRITE);
            // Search for unread emails
            FlagTerm unseenFlagTerm = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
            Message[] messages = folder.search(unseenFlagTerm);
            LOGGER.info("messages.length==" + (messages == null ? 0 : messages.length));
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
            LOGGER.log(Level.WARNING, "Failed to fetch unread emails", e);
            return List.of();
        } finally {
            closeFolder(folder);
            closeStore(store);
        }
    }

    /**
     * Parses a single email into an internal Envelope using Jakarta Mail.
     */
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
            String subject = msg.getSubject();
            subject = subject == null ? "" : subject;
            List<MailAttachment> attachments = new ArrayList<>();
            StringBuilder bodyBuilder = new StringBuilder();
            Object content = msg.getContent();
            extractContent(content, bodyBuilder, attachments);
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

    /**
     * Extracts the email string of the first address in Address[].
     */
    private String getFirstEmail(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return "";
        }
        return ((InternetAddress) addresses[0]).getAddress();
    }

    /**
     * Recursively extracts the body and attachments from a Jakarta Mail Part.
     */
    private void extractContent(
            Object content, StringBuilder body, List<MailAttachment> attachments) throws Exception {
        if (content instanceof String text) {
            if (body.length() == 0) {
                body.append(text);
            }
        } else if (content instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                Part part = mp.getBodyPart(i);
                extractPart(part, body, attachments);
            }
        }
    }

    /**
     * Recursively processes a MIME Part.
     */
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
            // Other types: attempt to read as text
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
                // Ignore unparseable Parts
            }
        }
    }

    // ======================== Jakarta Mail Session Tools ========================
    /**
     * Creates an IMAP Session.
     */
    private Session createImapSession(MailRuntimeConfig runtime) {
        Properties props = new Properties();
        props.put("mail.imap.host", runtime.imapHost());
        props.put("mail.imap.port", String.valueOf(runtime.imapPort()));
        props.put("mail.imap.connectiontimeout", MAIL_TIMEOUT);
        props.put("mail.imap.timeout", MAIL_TIMEOUT);
        if (runtime.imapSsl()) {
            props.put("mail.imap.ssl.enable", "true");
        }
        if (runtime.imapStartTls()) {
            props.put("mail.imap.starttls.enable", "true");
        }
        return Session.getInstance(props);
    }

    /**
     * Safely closes the Folder.
     */
    private void closeFolder(Folder folder) {
        if (folder != null) {
            try {
                folder.close(false);
            } catch (MessagingException ignore) {
                // Ignore
            }
        }
    }

    /**
     * Safely closes the Store.
     */
    private void closeStore(Store store) {
        if (store != null) {
            try {
                store.close();
            } catch (MessagingException ignore) {
                // Ignore
            }
        }
    }

    // ======================== Configuration Parsing ========================
    /**
     * Normalizes the sender's email: extracts the email from the 'Name <email>' format.
     */
    private String normalizeSender(String sender) {
        if (sender == null) {
            return "";
        }
        Matcher matcher = EMAIL_PATTERN.matcher(sender);
        if (matcher.find()) {
            return matcher.group(1).trim().toLowerCase(Locale.ROOT);
        }
        return sender.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Sleeps quietly.
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Null check utility.
     */
    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Parses the runtime email configuration (prioritizes presets, falls back to manual configuration).
     */
    private MailRuntimeConfig resolveRuntimeConfig(ChannelInfo channel) {
        EmailMailPreset preset =
                EmailPresetRegistry.presetOf(
                        channel == null ? null : EmailclawChannelConfig.getEmailAddress(channel));
        if (preset != null) {
            return new MailRuntimeConfig(
                    preset.imapHost(),
                    preset.imapPort(),
                    preset.imapSsl(),
                    preset.imapStartTls(),
                    preset.smtpHost(),
                    preset.smtpPort(),
                    preset.smtpSsl(),
                    preset.smtpStartTls());
        }
        if (channel == null) {
            return new MailRuntimeConfig("", 0, false, false, "", 0, false, false);
        }
        return new MailRuntimeConfig(
                EmailclawChannelConfig.getImapHost(channel),
                EmailclawChannelConfig.getImapPort(channel),
                EmailclawChannelConfig.isImapSsl(channel),
                EmailclawChannelConfig.isImapStartTls(channel),
                EmailclawChannelConfig.getSmtpHost(channel),
                EmailclawChannelConfig.getSmtpPort(channel),
                EmailclawChannelConfig.isSmtpSsl(channel),
                EmailclawChannelConfig.isSmtpStartTls(channel));
    }

    // ======================== Internal Data Structures ========================
    /**
     * Runtime email server configuration.
     */
    private record MailRuntimeConfig(
            String imapHost,
            int imapPort,
            boolean imapSsl,
            boolean imapStartTls,
            String smtpHost,
            int smtpPort,
            boolean smtpSsl,
            boolean smtpStartTls) {}

    /**
     * Email envelope: a parsed email.
     */
    private record EmailEnvelope(
            long uid,
            String from,
            String replyTo,
            String subject,
            String body,
            List<MailAttachment> attachments) {

        EmailEnvelope {
            // Defensive copy
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    /**
     * Email attachment.
     */
    private record MailAttachment(String filename, String contentType, byte[] data) {}
}

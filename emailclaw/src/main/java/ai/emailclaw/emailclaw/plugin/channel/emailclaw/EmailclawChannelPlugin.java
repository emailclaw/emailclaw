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
import ai.emailclaw.emailclaw.model.ChannelInfo;
import ai.emailclaw.emailclaw.model.ChatSessionInfo;
import ai.emailclaw.emailclaw.model.SessionDefaults;
import ai.emailclaw.emailclaw.plugin.AbstractChannelPlugin;
import ai.emailclaw.emailclaw.plugin.PluginStatus;
import ai.emailclaw.emailclaw.service.ChatService;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adapter for Emailclaw channel.
 * Note: This class will only be started after being written to the SPI file META-INF/services/ai.emailclaw.emailclaw.plugin.EmailclawPlugin.
 */
public class EmailclawChannelPlugin extends AbstractChannelPlugin {

    private static final Logger LOGGER = Logger.getLogger(EmailclawChannelPlugin.class.getName());

    private EmailclawChannelRunner runner;
    private volatile PluginStatus currentStatus = PluginStatus.registered();

    @Override
    public String id() {
        return ChannelIds.EMAILCLAW;
    }

    @Override
    public String displayName() {
        return "Emailclaw";
    }

    @Override
    public void replyToSession(String sessionId, String content) {
        if (runner == null || context == null) {
            LOGGER.log(
                    Level.WARNING,
                    "EmailclawPlugin not ready, unable to reply to session: {0}",
                    sessionId);
            return;
        }
        if (content == null || content.isBlank()) {
            LOGGER.log(
                    Level.WARNING,
                    "Reply content is empty, skipping session reply: {0}",
                    sessionId);
            return;
        }
        try {
            ChatService chatService = context.chatService();
            ChatSessionInfo session = chatService.findSession(sessionId);
            if (session == null) {
                LOGGER.log(
                        Level.WARNING, "Session does not exist, unable to reply: {0}", sessionId);
                return;
            }
            ChannelInfo channel = context.getChannelInfo(id());
            if (channel == null || !channel.isEnabled()) {
                LOGGER.log(Level.WARNING, "Emailclaw channel not enabled, unable to send email");
                return;
            }
            // If session.userId is not set, automatically take the first from
            // emailAllowlistSenders; note this is not an error return.
            String to = session.getUserId();
            if (to == null || to.isBlank() || SessionDefaults.LOCAL_USER_ID.equals(to)) {
                LOGGER.log(
                        Level.INFO,
                        "Session is missing a valid recipient address: sessionId={0}, userId={1};"
                                + " will take the first from emailAllowlistSenders",
                        new Object[] {sessionId, to});
                to = EmailclawChannelConfig.resolveDefaultRecipient(channel);
                session.setUserId(to);
            }
            // session.name saves the original email title, future email titles will be
            // "session.name session.id"
            String subject = session.getName() + " " + sessionId;
            runner.sendMail(channel, to, subject, content);
            LOGGER.log(
                    Level.INFO,
                    "EmailclawPlugin has sent reply to {0} (sessionId={1})",
                    new Object[] {to, sessionId});
        } catch (Exception e) {
            LOGGER.log(
                    Level.WARNING, "EmailclawPlugin failed to reply to session: " + sessionId, e);
        }
    }

    /**
     * Get the underlying EmailclawRunner instance.
     *
     * @return EmailclawRunner instance, returns null when not started
     */
    public EmailclawChannelRunner getRunner() {
        return runner;
    }

    @Override
    protected void doInitialize() {
        this.currentStatus = PluginStatus.initialized();
    }

    @Override
    public void start() {
        if (context == null) {
            currentStatus = PluginStatus.error("Not initialized");
            return;
        }
        try {
            runner =
                    new EmailclawChannelRunner(
                            context.channelService(),
                            context.chatService(),
                            context.agentService(),
                            context.providerService(),
                            context.configManager(),
                            context.projectService());
            currentStatus = PluginStatus.running("Polling emails");
            LOGGER.info("EmailclawPlugin has started");
        } catch (Exception e) {
            currentStatus = PluginStatus.error("Startup failed: " + e.getMessage());
            LOGGER.log(Level.WARNING, "EmailclawPlugin startup failed", e);
        }
    }

    @Override
    public void stop() {
        if (runner != null) {
            try {
                runner.stop();
                LOGGER.info("EmailclawPlugin has stopped");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "EmailclawPlugin stop exception", e);
            }
            runner = null;
        }
        currentStatus = PluginStatus.stopped();
    }

    @Override
    public PluginStatus status() {
        return currentStatus;
    }

    @Override
    public boolean normalizeConfig(Map<String, Object> pluginConfig) {
        boolean changed = EmailclawChannelConfig.normalizeEmailclawPluginConfig(pluginConfig);
        if (changed) {
            LOGGER.info(
                    "Emailclaw configuration has been normalized and cleaned up by the plugin"
                            + " itself during the persistence phase.");
        }
        return changed;
    }
}

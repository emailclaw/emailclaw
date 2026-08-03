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
package ai.emailclaw.emailclaw.channel.dingtalk;

import ai.emailclaw.emailclaw.plugin.AbstractChannelPlugin;
import ai.emailclaw.emailclaw.plugin.PluginStatus;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adapter for the DingTalk channel.
 * Note: This class will only be started after being written into the SPI file META-INF/services/ai.emailclaw.emailclaw.plugin.EmailclawPlugin.
 */
public class DingTalkPlugin extends AbstractChannelPlugin {
    public static final String ID_DINGTALK = "dingtalk";
    public static final String NAME_DINGTALK = "DingTalk";
    private static final Logger LOGGER = Logger.getLogger(DingTalkPlugin.class.getName());

    private DingTalkRunner runner;
    private volatile PluginStatus currentStatus = PluginStatus.registered();

    @Override
    public String id() {
        return ID_DINGTALK;
    }

    @Override
    public String displayName() {
        return NAME_DINGTALK;
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
            // Delegate to existing DingTalkRunner
            runner =
                    new DingTalkRunner(
                            context.channelService(),
                            context.chatService(),
                            context.agentService(),
                            context.providerService());
            currentStatus = PluginStatus.running("Listening in background");
            LOGGER.info("DingTalkPlugin started");
        } catch (Exception e) {
            currentStatus = PluginStatus.error("Failed to start: " + e.getMessage());
            LOGGER.log(Level.WARNING, "DingTalkPlugin failed to start", e);
        }
    }

    @Override
    public void stop() {
        if (runner != null) {
            try {
                runner.stop();
                LOGGER.info("DingTalkPlugin stopped");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "DingTalkPlugin stop exception", e);
            }
            runner = null;
        }
        currentStatus = PluginStatus.stopped();
    }

    @Override
    public PluginStatus status() {
        return currentStatus;
    }
}

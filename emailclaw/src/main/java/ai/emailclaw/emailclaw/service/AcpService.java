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

import ai.emailclaw.emailclaw.model.AcpAgentInfo;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ACP agent configuration service.
 *
 * <p>After refactoring, ConfigManager maintains the only configuration source.
 */
public class AcpService {
    private static final Logger LOGGER = Logger.getLogger(AcpService.class.getName());

    private final Object agentsLock = new Object();
    private final ConfigManager configManager;

    public AcpService(AppContext repository) {
        this.configManager = repository.configManager();
        ensureDefaults();
    }

    public List<AcpAgentInfo> list() {
        LOGGER.info("ACP call started: Read ACP agent list");
        return ensureDefaults();
    }

    public void add(AcpAgentInfo agent) {
        synchronized (agentsLock) {
            List<AcpAgentInfo> agents = ensureDefaults();
            agents.add(agent);
            configManager.saveAcpAgents(agents);
        }
        LOGGER.log(Level.INFO, "Added ACP agent: key={0}", agent.getKey());
    }

    public void remove(AcpAgentInfo agent) {
        synchronized (agentsLock) {
            List<AcpAgentInfo> agents = ensureDefaults();
            agents.removeIf(item -> item.getKey().equals(agent.getKey()));
            configManager.saveAcpAgents(agents);
        }
        LOGGER.log(Level.INFO, "Deleted ACP agent: key={0}", agent.getKey());
    }

    public void toggleEnabled(AcpAgentInfo agent) {
        synchronized (agentsLock) {
            agent.setEnabled(!agent.isEnabled());
            configManager.saveAcpAgents(ensureDefaults());
        }
    }

    public void save() {
        synchronized (agentsLock) {
            configManager.saveAcpAgents(ensureDefaults());
        }
    }

    private List<AcpAgentInfo> ensureDefaults() {
        synchronized (agentsLock) {
            List<AcpAgentInfo> agents = configManager.getAcpAgents();
            if (!agents.isEmpty()) {
                return agents;
            }
            LOGGER.info("ACP agent list is empty, loading default ACP agents");
            agents.add(new AcpAgentInfo("opencode", true, true, "opencode", "acp"));
            agents.add(new AcpAgentInfo("qwen_code", true, true, "qwen", "--acp"));
            agents.add(
                    new AcpAgentInfo(
                            "claude_code",
                            true,
                            true,
                            "npx",
                            "-y @zed-industries/claude-agent-acp"));
            agents.add(
                    new AcpAgentInfo("codex", true, true, "npx", "-y @zed-industries/codex-acp"));
            configManager.saveAcpAgents(agents);
            return agents;
        }
    }
}

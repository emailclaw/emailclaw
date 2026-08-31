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

import ai.emailclaw.emailclaw.model.SecurityRule;
import ai.emailclaw.emailclaw.model.SecuritySettings;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.AppPaths;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Security module service: Tool Guard rules, File Guard, Skill Scanner, and no-auth host configuration.
 */
public class SecurityService {
    private static final Logger LOGGER = Logger.getLogger(SecurityService.class.getName());

    private final Object lock = new Object();
    private final ConfigManager configManager;
    private final AppPaths paths;

    public SecurityService(AppContext repository) {
        this.configManager = repository.configManager();
        this.paths = repository.paths();
        ensureInitialized();
    }

    public SecuritySettings getSettings() {
        synchronized (lock) {
            return configManager.getSecuritySettings();
        }
    }

    public void saveSettings(SecuritySettings settings) {
        synchronized (lock) {
            configManager.saveSecuritySettings(settings);
            LOGGER.info("Saved security settings");
        }
    }

    /** Save Tool Guard detection rule list. */
    public void saveRules(List<SecurityRule> rules) {
        synchronized (lock) {
            configManager.saveSecurityRules(rules == null ? new ArrayList<>() : rules);
        }
    }

    public List<SecurityRule> listRules() {
        synchronized (lock) {
            return ensureRules();
        }
    }

    public void toggleRuleEnabled(SecurityRule rule) {
        synchronized (lock) {
            List<SecurityRule> rules = ensureRules();
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).ruleId().equals(rule.ruleId())) {
                    rules.set(i, rules.get(i).withEnabled(!rules.get(i).enabled()));
                    break;
                }
            }
            configManager.saveSecurityRules(rules);
        }
    }

    public void toggleRuleAutoDeny(SecurityRule rule) {
        synchronized (lock) {
            List<SecurityRule> rules = ensureRules();
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).ruleId().equals(rule.ruleId())) {
                    rules.set(i, rules.get(i).withAutoDeny(!rules.get(i).autoDeny()));
                    break;
                }
            }
            configManager.saveSecurityRules(rules);
        }
    }

    public void resetSettings() {
        synchronized (lock) {
            configManager.saveSecuritySettings(defaultSettings());
            List<SecurityRule> rules = seedDefaultRules();
            configManager.saveSecurityRules(rules);
        }
    }

    private void ensureInitialized() {
        synchronized (lock) {
            SecuritySettings settings = configManager.getSecuritySettings();
            boolean needsSave = false;
            if (settings.getToolGuard().getShellEvasionChecks() == null
                    || settings.getToolGuard().getShellEvasionChecks().isEmpty()) {
                settings.getToolGuard().setShellEvasionChecks(defaultShellEvasionChecks());
                needsSave = true;
            }
            if (settings.getFileGuard().getPaths().isEmpty()) {
                settings.getFileGuard().getPaths().add(defaultSecretPath());
                needsSave = true;
            }
            if (settings.getAllowNoAuthHosts().isEmpty()) {
                settings.getAllowNoAuthHosts().addAll(List.of("127.0.0.1", "::1"));
                needsSave = true;
            }
            if (needsSave) {
                configManager.saveSecuritySettings(settings);
            }
            ensureRules();
        }
    }

    private List<SecurityRule> ensureRules() {
        List<SecurityRule> rules = configManager.getSecurityRules();
        if (!rules.isEmpty()) {
            return rules;
        }
        LOGGER.info("Security rules are empty, writing built-in default rules");
        List<SecurityRule> defaults = seedDefaultRules();
        configManager.saveSecurityRules(defaults);
        return defaults;
    }

    private Optional<SecurityRule> findRule(List<SecurityRule> rules, String ruleId) {
        return rules.stream().filter(r -> r.ruleId().equals(ruleId)).findFirst();
    }

    private SecuritySettings defaultSettings() {
        SecuritySettings settings = new SecuritySettings();
        settings.getToolGuard().setEnabled(true);
        settings.getToolGuard().setShellEvasionChecks(defaultShellEvasionChecks());
        settings.getFileGuard().setEnabled(true);
        settings.getFileGuard().getPaths().add(defaultSecretPath());
        settings.getSkillScanner().setMode("warn");
        settings.getSkillScanner().setTimeout(30);
        settings.setAllowNoAuthHosts(new ArrayList<>(List.of("127.0.0.1", "::1")));
        return settings;
    }

    private String defaultSecretPath() {
        String secret = paths.secretDir.toAbsolutePath().toString();
        if (!secret.endsWith("/") && !secret.endsWith("\\")) {
            secret += java.io.File.separator;
        }
        return secret;
    }

    private Map<String, Boolean> defaultShellEvasionChecks() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("command_substitution", false);
        map.put("obfuscated_flags", false);
        map.put("backslash_escaped_whitespace", false);
        map.put("backslash_escaped_operators", false);
        map.put("newlines", false);
        map.put("comment_quote_desync", false);
        map.put("quoted_newline", false);
        return map;
    }

    private List<SecurityRule> seedDefaultRules() {
        List<SecurityRule> defaults = new ArrayList<>();

        // ========== Command Injection - 3 rules ==========
        defaults.add(
                rule(
                        "TOOL_CMD_DANGEROUS_RM",
                        "command_injection",
                        "CRITICAL",
                        "Detects dangerous rm -rf patterns in shell commands.",
                        true,
                        true));
        defaults.add(
                rule(
                        "TOOL_CMD_DANGEROUS_MV",
                        "command_injection",
                        "HIGH",
                        "Detects dangerous mv or mv -r patterns in shell commands.",
                        true,
                        true));
        defaults.add(
                rule(
                        "TOOL_CMD_FS_DESTRUCTION",
                        "command_injection",
                        "CRITICAL",
                        "Filesystem destruction patterns using dd, mkfs, or similar commands.",
                        true,
                        true));

        // ========== Resource Abuse - 4 rules ==========
        defaults.add(
                rule(
                        "TOOL_CMD_DOS_FORK_BOMB",
                        "resource_abuse",
                        "CRITICAL",
                        "Fork bombs or excessive process spawning patterns.",
                        true,
                        false));
        defaults.add(
                rule(
                        "TOOL_CMD_SYSTEM_REBOOT",
                        "resource_abuse",
                        "CRITICAL",
                        "System reboot or shutdown commands (reboot, poweroff, halt).",
                        true,
                        false));
        defaults.add(
                rule(
                        "TOOL_CMD_SERVICE_RESTART",
                        "resource_abuse",
                        "HIGH",
                        "Service restart or system service manipulation commands.",
                        true,
                        false));
        defaults.add(
                rule(
                        "TOOL_CMD_PROCESS_KILL",
                        "resource_abuse",
                        "HIGH",
                        "Killing critical system processes or services.",
                        true,
                        false));

        // ========== Code Execution - 8 rules ==========
        defaults.add(
                rule(
                        "TOOL_CMD_PIPE_TO_SHELL",
                        "code_execution",
                        "CRITICAL",
                        "Piping untrusted data directly to shell interpreters.",
                        true,
                        true));
        defaults.add(
                rule(
                        "TOOL_CMD_OBFUSCATED_EXEC",
                        "code_execution",
                        "HIGH",
                        "Obfuscated or encoded command execution patterns.",
                        true,
                        true));
        defaults.add(
                rule(
                        "TOOL_CMD_IFS_INJECTION",
                        "code_execution",
                        "HIGH",
                        "IFS (Internal Field Separator) injection for command bypass.",
                        true,
                        true));
        defaults.add(
                rule(
                        "TOOL_CMD_CONTROL_CHARS",
                        "code_execution",
                        "CRITICAL",
                        "Control character injection and null byte attacks.",
                        true,
                        true));
        defaults.add(
                rule(
                        "TOOL_CMD_UNICODE_WHITESPACE",
                        "code_execution",
                        "HIGH",
                        "Unicode whitespace or special character injection.",
                        true,
                        true));
        defaults.add(
                rule(
                        "TOOL_CMD_JQ_SYSTEM",
                        "code_execution",
                        "HIGH",
                        "jq system() function abuse for arbitrary command execution.",
                        true,
                        true));
        defaults.add(
                rule(
                        "TOOL_CMD_JQ_FILE_FLAGS",
                        "code_execution",
                        "HIGH",
                        "jq file operations with dangerous flags or paths.",
                        true,
                        false));
        defaults.add(
                rule(
                        "TOOL_CMD_ZSH_DANGEROUS",
                        "code_execution",
                        "HIGH",
                        "Dangerous zsh-specific features and globbing patterns.",
                        true,
                        false));

        // ========== Network Abuse - 1 rule ==========
        defaults.add(
                rule(
                        "TOOL_CMD_REVERSE_SHELL",
                        "network_abuse",
                        "CRITICAL",
                        "Reverse shell patterns (nc -e, bash -i >&) and outbound connections.",
                        true,
                        true));

        // ========== Sensitive File Access - 2 rules ==========
        defaults.add(
                rule(
                        "TOOL_CMD_SYSTEM_TAMPERING",
                        "sensitive_file_access",
                        "HIGH",
                        "Read or write access to /etc/passwd, /etc/shadow, or SSH keys.",
                        true,
                        true));
        defaults.add(
                rule(
                        "TOOL_CMD_PROC_ENVIRON",
                        "sensitive_file_access",
                        "HIGH",
                        "Access to /proc and environment variables containing secrets.",
                        true,
                        true));

        // ========== Privilege Escalation - 2 rules ==========
        defaults.add(
                rule(
                        "TOOL_CMD_UNSAFE_PERMISSIONS",
                        "privilege_escalation",
                        "HIGH",
                        "chmod, chown, or umask modifications on system directories.",
                        true,
                        true));
        defaults.add(
                rule(
                        "TOOL_CMD_PRIVILEGE_ESCALATION",
                        "privilege_escalation",
                        "CRITICAL",
                        "Use of sudo, su, or setuid/setgid for privilege escalation.",
                        true,
                        true));

        return defaults;
    }

    private SecurityRule rule(
            String id,
            String category,
            String severity,
            String description,
            boolean enabled,
            boolean autoDeny) {
        return new SecurityRule(id, category, severity, description, true, enabled, autoDeny);
    }

    /** Parse a comma-separated tool list into a list. */
    public static List<String> parseToolList(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    public static String joinToolList(List<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return "";
        }
        return String.join(", ", tools);
    }

    /** Validate IPv4/IPv6 address format (simplified version, consistent with Emailclaw frontend logic). */
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        String trimmed = ip.trim();
        if (trimmed.contains(":")) {
            return trimmed.matches("^[0-9a-fA-F:]+$");
        }
        return trimmed.matches(
                "^(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\."
                        + "(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\."
                        + "(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\."
                        + "(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");
    }

    public static boolean isDefaultNoAuthHost(String host) {
        return "127.0.0.1".equals(host) || "::1".equals(host);
    }

    public static String categoryLabel(String category) {
        if (category == null || category.isBlank()) {
            return "Other";
        }
        return switch (category) {
            case "command_injection" -> "Command Injection";
            case "resource_abuse" -> "Resource Abuse";
            case "code_execution" -> "Code Execution";
            case "network_abuse" -> "Network Abuse";
            case "sensitive_file_access" -> "Sensitive File Access";
            case "privilege_escalation" -> "Privilege Escalation";
            default -> category.replace('_', ' ');
        };
    }
}

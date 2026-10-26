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

import ai.emailclaw.emailclaw.channel.ChannelPluginConfigAccess;
import ai.emailclaw.emailclaw.model.AgentIds;
import ai.emailclaw.emailclaw.model.ChannelInfo;
import ai.emailclaw.emailclaw.model.DeliveryMode;
import ai.emailclaw.emailclaw.util.CommonUtils;
import ai.emailclaw.emailclaw.util.UuidUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Typed accessor for Email channel {@link ChannelInfo#getPluginConfig()}.
 * Manages multi-mailbox configurations and channel-level parameters.
 */
public final class EmailclawChannelConfig {

    private static final Logger LOGGER = Logger.getLogger(EmailclawChannelConfig.class.getName());

    private EmailclawChannelConfig() {}

    /**
     * Retrieves all configured mailboxes from the channel configuration.
     *
     * @param channel Channel configuration
     * @return List of MailboxAccountConfig
     */
    public static List<MailboxAccountConfig> getMailboxes(ChannelInfo channel) {
        if (channel == null || channel.getPluginConfig() == null) {
            return List.of();
        }
        return getMailboxes(channel.getPluginConfig());
    }

    /**
     * Retrieves all configured mailboxes from the plugin config map.
     *
     * @param pluginConfig Plugin configuration map
     * @return List of MailboxAccountConfig
     */
    @SuppressWarnings("unchecked")
    public static List<MailboxAccountConfig> getMailboxes(Map<String, Object> pluginConfig) {
        if (pluginConfig == null) {
            return List.of();
        }
        Object raw = pluginConfig.get(EmailclawChannelConfigKeys.MAILBOXES);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<MailboxAccountConfig> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                try {
                    result.add(fromMap((Map<String, Object>) map));
                } catch (Exception e) {
                    LOGGER.warning("Failed to parse mailbox entry: " + e.getMessage());
                }
            }
        }
        return List.copyOf(result);
    }

    /**
     * Saves the list of mailboxes to the channel configuration.
     *
     * @param channel   Channel configuration
     * @param mailboxes List of MailboxAccountConfig
     */
    public static void setMailboxes(ChannelInfo channel, List<MailboxAccountConfig> mailboxes) {
        if (channel == null) {
            return;
        }
        if (channel.getPluginConfig() == null) {
            channel.setPluginConfig(new LinkedHashMap<>());
        }
        setMailboxes(channel.getPluginConfig(), mailboxes);
    }

    /**
     * Saves the list of mailboxes to the plugin config map.
     *
     * @param pluginConfig Plugin configuration map
     * @param mailboxes    List of MailboxAccountConfig
     */
    public static void setMailboxes(
            Map<String, Object> pluginConfig, List<MailboxAccountConfig> mailboxes) {
        if (pluginConfig == null) {
            return;
        }
        List<Map<String, Object>> serialized = new ArrayList<>();
        if (mailboxes != null) {
            for (MailboxAccountConfig mb : mailboxes) {
                if (mb != null && !mb.emailAddress().isBlank()) {
                    serialized.add(toMap(mb));
                }
            }
        }
        pluginConfig.put(EmailclawChannelConfigKeys.MAILBOXES, serialized);
    }

    /**
     * Adds or updates a mailbox configuration in the channel.
     * Enforces unique emailAddress across all mailboxes.
     *
     * @param channel Channel configuration
     * @param mailbox Mailbox account to add or update
     */
    public static void addOrUpdateMailbox(ChannelInfo channel, MailboxAccountConfig mailbox) {
        if (channel == null || mailbox == null) {
            return;
        }
        List<MailboxAccountConfig> current = new ArrayList<>(getMailboxes(channel));
        String targetEmail = mailbox.emailAddress();
        String targetId = mailbox.id();

        current.removeIf(
                m -> m.id().equals(targetId) || m.emailAddress().equalsIgnoreCase(targetEmail));
        current.add(mailbox);
        setMailboxes(channel, current);
    }

    /**
     * Removes a mailbox by ID.
     *
     * @param channel   Channel configuration
     * @param mailboxId Mailbox ID to remove
     */
    public static void removeMailbox(ChannelInfo channel, String mailboxId) {
        if (channel == null || mailboxId == null || mailboxId.isBlank()) {
            return;
        }
        List<MailboxAccountConfig> current = new ArrayList<>(getMailboxes(channel));
        boolean removed = current.removeIf(m -> m.id().equals(mailboxId));
        if (removed) {
            setMailboxes(channel, current);
        }
    }

    /**
     * Finds a mailbox by its unique ID.
     *
     * @param channel   Channel configuration
     * @param mailboxId Mailbox ID
     * @return Optional containing MailboxAccountConfig if found
     */
    public static Optional<MailboxAccountConfig> findMailboxById(
            ChannelInfo channel, String mailboxId) {
        if (mailboxId == null || mailboxId.isBlank()) {
            return Optional.empty();
        }
        return getMailboxes(channel).stream().filter(m -> mailboxId.equals(m.id())).findFirst();
    }

    /**
     * Finds a mailbox by email address (case-insensitive).
     *
     * @param channel      Channel configuration
     * @param emailAddress Email address to find
     * @return Optional containing MailboxAccountConfig if found
     */
    public static Optional<MailboxAccountConfig> findMailboxByEmail(
            ChannelInfo channel, String emailAddress) {
        if (emailAddress == null || emailAddress.isBlank()) {
            return Optional.empty();
        }
        String normalized = emailAddress.trim().toLowerCase(Locale.ROOT);
        return getMailboxes(channel).stream()
                .filter(m -> normalized.equals(m.emailAddress()))
                .findFirst();
    }

    /**
     * Resolves the primary outbound mailbox for sending replies.
     * Prioritizes matching originMailboxId, and falls back to the first runnable mailbox.
     *
     * @param channel         Channel configuration
     * @param originMailboxId Origin mailbox ID if known (can be null)
     * @return Optional containing selected MailboxAccountConfig
     */
    public static Optional<MailboxAccountConfig> resolveOutboundMailbox(
            ChannelInfo channel, String originMailboxId) {
        List<MailboxAccountConfig> mailboxes = getMailboxes(channel);
        if (mailboxes.isEmpty()) {
            return Optional.empty();
        }
        if (originMailboxId != null && !originMailboxId.isBlank()) {
            Optional<MailboxAccountConfig> matched =
                    mailboxes.stream()
                            .filter(m -> m.id().equals(originMailboxId) && m.isRunnable())
                            .findFirst();
            if (matched.isPresent()) {
                return matched;
            }
        }
        return mailboxes.stream().filter(MailboxAccountConfig::isRunnable).findFirst();
    }

    /**
     * Resolves default recipient for a given mailbox, falling back to global allowlist.
     *
     * @param channel Channel configuration
     * @param mailbox Mailbox configuration (can be null)
     * @return First non-blank allowlist email address or null
     */
    public static String resolveDefaultRecipient(
            ChannelInfo channel, MailboxAccountConfig mailbox) {
        if (mailbox != null && !mailbox.allowlistSenders().isEmpty()) {
            for (String sender : mailbox.allowlistSenders()) {
                if (CommonUtils.notBlank(sender)) {
                    return sender.trim();
                }
            }
        }
        return null;
    }

    /** Deep copy the current plugin configuration for background task snapshots. */
    public static Map<String, Object> copyPluginConfig(ChannelInfo channel) {
        return new LinkedHashMap<>(ChannelPluginConfigAccess.config(channel));
    }

    /**
     * Normalizes the plugin configuration:
     * 1. Validates and enforces uniqueness of emailAddress across mailboxes.
     * 2. Cleans up redundant entries.
     *
     * @param ch Channel configuration
     * @return True if modified
     */
    public static boolean normalizeEmailclawPluginConfig(ChannelInfo ch) {
        if (ch == null || ch.getPluginConfig() == null) return false;
        boolean changed = normalizeEmailclawPluginConfig(ch.getPluginConfig());
        if (changed) {
            List<MailboxAccountConfig> mailboxes = getMailboxes(ch);
            boolean hasRunnable = mailboxes.stream().anyMatch(MailboxAccountConfig::isRunnable);
            if (hasRunnable && !ch.isEnabled()) {
                ch.setEnabled(true);
            }
        }
        return changed;
    }

    /**
     * Normalizes the plugin config map.
     *
     * @param pluginConfig Plugin configuration map
     * @return True if modified
     */
    public static boolean normalizeEmailclawPluginConfig(Map<String, Object> pluginConfig) {
        if (pluginConfig == null) return false;
        boolean changed = false;
        if (pluginConfig.containsKey("enabled")) {
            pluginConfig.remove("enabled");
            changed = true;
        }
        if (pluginConfig.containsKey("botPrefix")) {
            pluginConfig.remove("botPrefix");
            changed = true;
        }

        if (processOneTimePasswordRegistrations(pluginConfig)) {
            changed = true;
        }

        if (!pluginConfig.containsKey(EmailclawChannelConfigKeys.MAILBOXES)
                || !(pluginConfig.get(EmailclawChannelConfigKeys.MAILBOXES) instanceof List)) {
            pluginConfig.put(EmailclawChannelConfigKeys.MAILBOXES, new ArrayList<>());
            changed = true;
        }

        List<MailboxAccountConfig> mailboxes = getMailboxes(pluginConfig);
        if (mailboxes.isEmpty()) {
            return changed;
        }

        Set<String> seenEmails = new HashSet<>();
        List<MailboxAccountConfig> deduplicated = new ArrayList<>();

        for (MailboxAccountConfig mb : mailboxes) {
            String email = mb.emailAddress();
            if (email.isBlank()) {
                changed = true;
                continue;
            }
            if (seenEmails.add(email)) {
                if (EmailPresetRegistry.presetOf(email) != null) {
                    if (!mb.imapHost().isEmpty() || !mb.smtpHost().isEmpty()) {
                        mb =
                                new MailboxAccountConfig(
                                        mb.id(),
                                        mb.name(),
                                        mb.enabled(),
                                        mb.emailAddress(),
                                        mb.emailPassword(),
                                        "",
                                        993,
                                        true,
                                        false,
                                        "",
                                        465,
                                        true,
                                        false,
                                        mb.targetAgentId(),
                                        mb.allowlistSenders(),
                                        mb.pollIntervalSeconds(),
                                        mb.deliveryMode());
                        changed = true;
                    }
                }
                deduplicated.add(mb);
            } else {
                changed = true;
                LOGGER.warning("Removed duplicate mailbox entry for emailAddress: " + email);
            }
        }

        if (changed) {
            setMailboxes(pluginConfig, deduplicated);
        }
        return changed;
    }

    /**
     * Inspects the plugin configuration for any pending one-time password (OTP) registrations.
     *
     * <p>When Emailclaw runs in headless daemon mode (e.g., {@code --service}), users cannot configure
     * mailboxes via the graphical settings dialog. Instead, they can supply {@code registrantEmail}
     * and {@code oneTimePassword} in {@code channels.json}. This method performs the background
     * registration via {@link OneTimePasswordAuth}, populates the allocated {@code emailAddress} and
     * {@code emailPassword}, and appends {@code registrantEmail} to {@code allowlistSenders}.
     *
     * <p>To prevent infinite retry storms against the authentication endpoint, {@code oneTimePassword}
     * is ALWAYS removed from the map after the authentication attempt, regardless of whether it succeeded
     * or failed.
     *
     * @param pluginConfig the raw plugin configuration map to inspect and update
     * @return {@code true} if any configuration modification occurred
     */
    private static boolean processOneTimePasswordRegistrations(Map<String, Object> pluginConfig) {
        if (pluginConfig == null) {
            return false;
        }
        boolean changed = false;

        // 1. Process pending registrations within the "mailboxes" list
        Object rawMailboxes = pluginConfig.get(EmailclawChannelConfigKeys.MAILBOXES);
        if (rawMailboxes instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> rawMap) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mailboxMap = (Map<String, Object>) rawMap;
                    String emailAddress = (String) mailboxMap.getOrDefault("emailAddress", "");
                    String emailPassword = (String) mailboxMap.getOrDefault("emailPassword", "");
                    String registrantEmail = (String) mailboxMap.get("registrantEmail");
                    String oneTimePassword = (String) mailboxMap.get("oneTimePassword");

                    if ((emailAddress == null
                                    || emailAddress.isBlank()
                                    || emailPassword == null
                                    || emailPassword.isBlank())
                            && registrantEmail != null
                            && !registrantEmail.isBlank()
                            && oneTimePassword != null
                            && !oneTimePassword.isBlank()) {

                        String trimmedRegistrant = registrantEmail.trim();
                        String trimmedOtp = oneTimePassword.trim();

                        // Always remove oneTimePassword to prevent retry storms
                        mailboxMap.remove("oneTimePassword");
                        changed = true;

                        LOGGER.info(
                                "Attempting background One-Time Password registration for"
                                        + " registrant: "
                                        + trimmedRegistrant);
                        OneTimePasswordAuth.EmailAndPassword authRes =
                                OneTimePasswordAuth.oneTimePasswordAuth(
                                        trimmedRegistrant, trimmedOtp);

                        if (authRes != null
                                && authRes.email() != null
                                && !authRes.email().isBlank()) {
                            mailboxMap.put("emailAddress", authRes.email());
                            mailboxMap.put("emailPassword", authRes.password());

                            String currentId = (String) mailboxMap.get("id");
                            if (currentId == null || currentId.isBlank()) {
                                mailboxMap.put("id", UuidUtils.randomUUIDv7().toString());
                            }

                            String currentName = (String) mailboxMap.get("name");
                            if (currentName == null
                                    || currentName.isBlank()
                                    || "Mailbox".equalsIgnoreCase(currentName.trim())) {
                                mailboxMap.put("name", authRes.email());
                            }
                            if (!mailboxMap.containsKey("enabled")) {
                                mailboxMap.put("enabled", true);
                            }

                            // Automatically add registrantEmail to allowlistSenders
                            addSenderToAllowlist(mailboxMap, trimmedRegistrant);

                            LOGGER.info(
                                    "Background registration succeeded for registrant "
                                            + trimmedRegistrant
                                            + ", allocated email: "
                                            + authRes.email());
                        } else {
                            LOGGER.severe(
                                    "Background registration failed for registrant: "
                                            + trimmedRegistrant
                                            + ". oneTimePassword was purged to prevent retry"
                                            + " storms.");
                        }
                    } else if (mailboxMap.containsKey("oneTimePassword")
                            && emailAddress != null
                            && !emailAddress.isBlank()
                            && emailPassword != null
                            && !emailPassword.isBlank()) {
                        // Already allocated, clean up residual oneTimePassword
                        mailboxMap.remove("oneTimePassword");
                        changed = true;
                    }
                }
            }
        }

        // 2. Process pending registration at the root level of pluginConfig
        String rootRegistrantEmail = (String) pluginConfig.get("registrantEmail");
        String rootOtp = (String) pluginConfig.get("oneTimePassword");
        if (rootRegistrantEmail != null
                && !rootRegistrantEmail.isBlank()
                && rootOtp != null
                && !rootOtp.isBlank()) {

            String trimmedRegistrant = rootRegistrantEmail.trim();
            String trimmedOtp = rootOtp.trim();

            // Always remove oneTimePassword to prevent retry storms
            pluginConfig.remove("oneTimePassword");
            changed = true;

            LOGGER.info(
                    "Attempting root-level One-Time Password registration for registrant: "
                            + trimmedRegistrant);
            OneTimePasswordAuth.EmailAndPassword authRes =
                    OneTimePasswordAuth.oneTimePasswordAuth(trimmedRegistrant, trimmedOtp);

            if (authRes != null && authRes.email() != null && !authRes.email().isBlank()) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> mailboxList =
                        (rawMailboxes instanceof List<?>)
                                ? (List<Map<String, Object>>) rawMailboxes
                                : new ArrayList<>();

                if (!(rawMailboxes instanceof List<?>)) {
                    pluginConfig.put(EmailclawChannelConfigKeys.MAILBOXES, mailboxList);
                }

                boolean existingFound = false;
                for (Map<String, Object> mb : mailboxList) {
                    String mbEmail = (String) mb.get("emailAddress");
                    if (authRes.email().equalsIgnoreCase(mbEmail)) {
                        mb.put("emailPassword", authRes.password());
                        addSenderToAllowlist(mb, trimmedRegistrant);
                        existingFound = true;
                        break;
                    }
                }

                if (!existingFound) {
                    Map<String, Object> newMailbox = new LinkedHashMap<>();
                    newMailbox.put("id", UuidUtils.randomUUIDv7().toString());
                    newMailbox.put("name", authRes.email());
                    newMailbox.put("enabled", true);
                    newMailbox.put("emailAddress", authRes.email());
                    newMailbox.put("emailPassword", authRes.password());
                    newMailbox.put("registrantEmail", trimmedRegistrant);
                    List<String> allowlist = new ArrayList<>();
                    allowlist.add(trimmedRegistrant);
                    newMailbox.put("allowlistSenders", allowlist);
                    mailboxList.add(newMailbox);
                }

                LOGGER.info(
                        "Root-level registration succeeded for registrant "
                                + trimmedRegistrant
                                + ", allocated email: "
                                + authRes.email());
            } else {
                LOGGER.severe(
                        "Root-level registration failed for registrant: "
                                + trimmedRegistrant
                                + ". oneTimePassword was purged to prevent retry storms.");
            }
        } else if (pluginConfig.containsKey("oneTimePassword")) {
            pluginConfig.remove("oneTimePassword");
            changed = true;
        }

        return changed;
    }

    /**
     * Appends the given sender email to the mailbox allowlist if not already present.
     *
     * @param mailboxMap  the mailbox configuration map
     * @param senderEmail the registrant sender email to allow
     */
    private static void addSenderToAllowlist(Map<String, Object> mailboxMap, String senderEmail) {
        if (mailboxMap == null || senderEmail == null || senderEmail.isBlank()) {
            return;
        }
        String normalizedSender = senderEmail.trim();
        Object rawSenders = mailboxMap.get("allowlistSenders");
        List<String> sendersList = new ArrayList<>();
        if (rawSenders instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !item.toString().isBlank()) {
                    sendersList.add(item.toString().trim());
                }
            }
        }
        boolean alreadyPresent = sendersList.stream().anyMatch(normalizedSender::equalsIgnoreCase);
        if (!alreadyPresent) {
            sendersList.add(normalizedSender);
            mailboxMap.put("allowlistSenders", sendersList);
            LOGGER.info(
                    "Automatically added registrant email "
                            + normalizedSender
                            + " to allowlistSenders");
        }
    }

    private static Map<String, Object> toMap(MailboxAccountConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", config.id());
        map.put("name", config.name());
        map.put("enabled", config.enabled());
        map.put("emailAddress", config.emailAddress());
        map.put("emailPassword", config.emailPassword());

        boolean isPreset = EmailPresetRegistry.presetOf(config.emailAddress()) != null;
        if (!isPreset) {
            map.put("imapHost", config.imapHost());
            map.put("imapPort", config.imapPort());
            map.put("imapSsl", config.imapSsl());
            map.put("imapStartTls", config.imapStartTls());
            map.put("smtpHost", config.smtpHost());
            map.put("smtpPort", config.smtpPort());
            map.put("smtpSsl", config.smtpSsl());
            map.put("smtpStartTls", config.smtpStartTls());
        }

        if (config.targetAgentId() != null
                && !config.targetAgentId().isBlank()
                && !AgentIds.DEFAULT.equalsIgnoreCase(config.targetAgentId().trim())) {
            map.put("targetAgentId", config.targetAgentId().trim());
        }
        if (config.allowlistSenders() != null && !config.allowlistSenders().isEmpty()) {
            map.put("allowlistSenders", config.allowlistSenders());
        }
        if (config.pollIntervalSeconds() != 30) {
            map.put("pollIntervalSeconds", config.pollIntervalSeconds());
        }
        if (config.deliveryMode() != null && config.deliveryMode() != DeliveryMode.FINAL) {
            map.put("deliveryMode", config.deliveryMode().getValue());
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private static MailboxAccountConfig fromMap(Map<String, Object> map) {
        String id = (String) map.getOrDefault("id", "");
        String name = (String) map.getOrDefault("name", "");
        boolean enabled = Boolean.TRUE.equals(map.getOrDefault("enabled", true));
        String emailAddress = (String) map.getOrDefault("emailAddress", "");
        String emailPassword = (String) map.getOrDefault("emailPassword", "");
        String imapHost = (String) map.getOrDefault("imapHost", "");
        int imapPort = toInt(map.get("imapPort"), 993);
        boolean imapSsl = toBool(map.get("imapSsl"), true);
        boolean imapStartTls = toBool(map.get("imapStartTls"), false);
        String smtpHost = (String) map.getOrDefault("smtpHost", "");
        int smtpPort = toInt(map.get("smtpPort"), 465);
        boolean smtpSsl = toBool(map.get("smtpSsl"), true);
        boolean smtpStartTls = toBool(map.get("smtpStartTls"), false);
        String targetAgentId = (String) map.getOrDefault("targetAgentId", AgentIds.DEFAULT);
        if (targetAgentId == null || targetAgentId.isBlank()) {
            targetAgentId = AgentIds.DEFAULT;
        }
        List<String> allowlistSenders = new ArrayList<>();
        Object allowlistObj = map.get("allowlistSenders");
        if (allowlistObj instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    allowlistSenders.add(o.toString());
                }
            }
        }
        int pollIntervalSeconds = toInt(map.get("pollIntervalSeconds"), 30);
        DeliveryMode deliveryMode = DeliveryMode.fromValue((String) map.get("deliveryMode"));

        return new MailboxAccountConfig(
                id,
                name,
                enabled,
                emailAddress,
                emailPassword,
                imapHost,
                imapPort,
                imapSsl,
                imapStartTls,
                smtpHost,
                smtpPort,
                smtpSsl,
                smtpStartTls,
                targetAgentId,
                allowlistSenders,
                pollIntervalSeconds,
                deliveryMode);
    }

    private static int toInt(Object value, int defaultVal) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultVal;
    }

    private static boolean toBool(Object value, boolean defaultVal) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return Boolean.parseBoolean(s.trim());
        }
        return defaultVal;
    }
}

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

import ai.emailclaw.emailclaw.model.BackupInfo;
import ai.emailclaw.emailclaw.storage.AppContext;
import ai.emailclaw.emailclaw.storage.ConfigManager;
import ai.emailclaw.emailclaw.util.UuidUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Responsible for scheduled backup and rotation management of core configuration (GlobalConfig).
 *
 * <p>This service runs silently in the background and regularly saves configuration snapshots. When a file is corrupted or an exception occurs,
 * the system can prompt the user to recover from the latest backup at startup to ensure data security.
 */
public class BackupService {
    private static final Logger LOGGER = Logger.getLogger(BackupService.class.getName());

    private final Object backupsLock = new Object();
    private final ConfigManager configManager;

    public BackupService(AppContext repository) {
        this.configManager = repository.configManager();
    }

    public List<BackupInfo> list() {
        return configManager.getBackups();
    }

    public BackupInfo create(
            /**
             * Scheduler for timed backup tasks.
             */
            String name,
            String description,
            boolean includeAgents,
            boolean includeGlobalConfig,
            boolean includeSecrets,
            boolean includeSkillPool) {
        BackupInfo info =
                new BackupInfo(
                        "emailclaw-" + UuidUtils.randomUUIDv7().toString().substring(28),
                        name,
                        description,
                        LocalDateTime.now().toString(),
                        /**
                         * Default number of backup files to keep.
                         * When the number of backups exceeds this value, the oldest backups will be automatically deleted.
                         */
                        0,
                        includeAgents,
                        includeGlobalConfig,
                        includeSecrets,
                        includeSkillPool);
        synchronized (backupsLock) {
            List<BackupInfo> backups = configManager.getBackups();
            backups.add(0, info);
            configManager.saveBackups(backups);
        }
        LOGGER.log(
                Level.INFO,
                "Successfully created backup: id={0}, name={1}",
                new Object[] {info.id(), info.name()});
        return info;
    }

    public void remove(BackupInfo backup) {
        synchronized (backupsLock) {
            List<BackupInfo> backups = configManager.getBackups();
            backups.removeIf(item -> item.id().equals(backup.id()));
            configManager.saveBackups(backups);
        }
        LOGGER.log(Level.INFO, "Deleted backup: id={0}", backup.id());
    }

    public void save() {
        synchronized (backupsLock) {
            configManager.saveBackups(configManager.getBackups());
        }
    }
}

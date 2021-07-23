package ratismal.drivebackup.config;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.util.Logger;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.util.MessageUtil;

import static ratismal.drivebackup.config.Localization.intl;

public class ConfigMigrator {
    private static final String DEFAULT_TIMEZONE_STRING = "-00:00";

    FileConfiguration config;
    FileConfiguration localizationConfig;
    List<CommandSender> initiators;

    public ConfigMigrator(FileConfiguration config, FileConfiguration localizationConfig, List<CommandSender> initiators) {
        this.config = config;
        this.localizationConfig = localizationConfig;
        this.initiators = initiators;
    }

    public void migrate() {
        Logger logger = (input, placeholders) -> {
            MessageUtil.Builder().mmText(input, placeholders).to(initiators).send();
        };

        if (config.contains("version") && config.getInt("version") >= Config.VERSION) {
            return;
        }

        logger.log(intl("config-migrating"), "version", String.valueOf(Config.VERSION));
        config.set("version", 2);

        int backupThreadPriority = config.getInt("backup-thread-priority");
        if (backupThreadPriority < 1) {
            config.set("backup-thread-priority", 1);
        }

        int zipCompression = config.getInt("zip-compression");
        if (zipCompression < 1) {
            config.set("zip-compression", 1);
        }

        if (config.getString("schedule-timezone") != null && !config.getString("schedule-timezone").equals(DEFAULT_TIMEZONE_STRING)) {
            migrate("schedule-timezone", "advanced.date-timezone");
            config.set("backup-format-timezone", null);
        } else {
            migrate("backup-format-timezone", "advanced.date-timezone");
            config.set("schedule-timezone", null);
        }

        String googleDriveSharedDriveId = config.getString("googledrive.shared-drive-id");
        if (googleDriveSharedDriveId == null) {
            config.set("googledrive.shared-drive-id", "");
        }

        migrate("advanced.message-prefix", "messages.prefix");
        migrate("advanced.default-message-color", "messages.default-color");

        migrateIntl("messages.no-perm", "no-perm");
        migrateIntl("messages.backup-start", "backup-start");
        migrateIntl("messages.backup-complete", "backup-complete");
        migrateIntl("messages.next-backup", "next-backup");
        migrateIntl("messages.next-schedule-backup", "next-schedule-backup");
        migrateIntl("messages.next-schedule-backup-format", "next-schedule-backup-format");
        migrateIntl("messages.auto-backups-disabled", "auto-backups-disabled");
        
        DriveBackup.getInstance().saveConfig();
        DriveBackup.getInstance().saveIntlConfig();
    }

    /**
     * Migrates a setting from the specified old path in the config
     * to the new path
     * @param oldPath the old path
     * @param newPath the new path
     */
    public void migrate(String oldPath, String newPath) {
        config.set(newPath, config.get(oldPath));
        config.set(oldPath, null);
    }

    /**
     * Migrates a setting from the specified old path in the config
     * to the new path in the localization config
     * @param oldPath the old path
     * @param newPath the new path
     */
    public void migrateIntl(String oldPath, String newPath) {
        localizationConfig.set(newPath, config.get(oldPath));
        config.set(oldPath, null);
    }
}

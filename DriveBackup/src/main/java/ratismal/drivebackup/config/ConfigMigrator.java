package ratismal.drivebackup.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.ObjectUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.util.CustomConfig;
import ratismal.drivebackup.util.Logger;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.util.MessageUtil;

import java.io.InputStream;
import java.io.InputStreamReader;

public class ConfigMigrator {
    private static final String DEFAULT_TIMEZONE_STRING = "-00:00";

    // ConfigMigrator is called before localization is parsed, since ConfigMigrator
    // may change the intl file. Therefore, we just hardcode any messages.
    private static final String MIGRATING_MESSAGE = "Automatically migrating config to version <version>";
    private static final String UPDATING_INTL_MESSAGE = "Automatically updating intl config to latest version (no user modifications detected)";

    private static final Set<String> KNOWN_DEFAULT_INTL_HASHES = new HashSet<>(Arrays.asList(
        "a4318fb0fcfc9508d66cb5e07221f287914c750ba0fc8388fd47535bf747a36b", // v1.8.1-
        "cb18f3edd7b8af5fd59d0d545adf23439e1c14686c79b62c03f953ace5b5642c", // v1.8.0
        "240e14212dfd3d48d855e7bf387f9e444cbb33305ccb5b3820bf7e0ba7f62c26", // v1.7.0
        "b2e9699dd99cc975ed938ddcf9b466e7c5348e8985c547547a1eba429db1644b", // v1.6.2-3
        "f5c2f75a44569bde08020323b6045faf4aca0563b8f3472712bd68a53428e298", // v1.6.1
        "cfccd3e5f28adb640fcb5717a6c43cac4be6c6927271157a10635a65bce54931", // v1.6.0
        "0ce295bca8664327df64d88dcb1e58c94d0c30a8389729a499efee85801b969c", // v1.5.4
        "8a452d3ea74753193a3113c4cc5b970c29662d2a08622407d08c8fda33df371a", // v1.5.2-3
        "1d90033798544849264e7139b11f5d1faaf0c950f028c05057eb6580101c5f84" // v1.5.0-1
    ));

    private FileConfiguration config;
    private CustomConfig localizationConfig;
    private List<CommandSender> initiators;

    public ConfigMigrator(FileConfiguration config, CustomConfig localizationConfig,
            List<CommandSender> initiators) {
        this.config = config;
        this.localizationConfig = localizationConfig;
        this.initiators = initiators;
    }

    public void migrate() {
        Logger logger = (input, placeholders) -> MessageUtil.Builder().mmText(input, placeholders).to(initiators).send();

        if (config.isSet("version") && config.getInt("version") >= Config.VERSION) {
            fastTrackIntlConfig(logger);
            return;
        }
        logger.log(MIGRATING_MESSAGE, "version", String.valueOf(Config.VERSION));
        config.set("version", Config.VERSION);
        int backupThreadPriority = config.getInt("backup-thread-priority");
        if (backupThreadPriority < 1) {
            config.set("backup-thread-priority", 1);
        }
        int zipCompression = config.getInt("zip-compression");
        if (zipCompression < 1) {
            config.set("zip-compression", 1);
        }
        migrate("dir", "local-save-directory");
        migrate("destination", "remote-save-directory");
        if (config.isSet("schedule-timezone")
                && !ObjectUtils.equals(config.getString("schedule-timezone"), DEFAULT_TIMEZONE_STRING)) {
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

        fastTrackIntlConfig(logger);

        DriveBackup.getInstance().saveConfig();
        DriveBackup.getInstance().saveIntlConfig();
    }

    /**
     * Checks if the intl config file matches a known default version hash.
     * If it does, automatically replaces it with the latest default version.
     * 
     * @param logger the logger to use for messages
     */
    private void fastTrackIntlConfig(Logger logger) {
        try {
            InputStream defIntlStream = DriveBackup.getInstance().getResource("intl.yml");

            if (defIntlStream == null) {
                return;
            }

            FileConfiguration defaultIntl = YamlConfiguration.loadConfiguration(new InputStreamReader(defIntlStream, StandardCharsets.UTF_8));

            String currentHash = calculateIntlHash(localizationConfig.getConfig());
            String latestDefaultHash = calculateIntlHash(defaultIntl);

            if (currentHash.equals(latestDefaultHash)) {
                return;
            }

            if (KNOWN_DEFAULT_INTL_HASHES.contains(currentHash)) {
                logger.log(UPDATING_INTL_MESSAGE);

                DriveBackup.getInstance().saveResource("intl.yml", true);
            }
        } catch (Exception e) {
            logger.log("Unable to migrate intl file to latest version");
            e.printStackTrace();
        }
    }

    /**
     * Calculates a hash of the intl config content for comparison.
     * 
     * @param config the config to hash
     * @return the SHA-256 hash as a hex string
     */
    private String calculateIntlHash(FileConfiguration config) {
        try {
            StringBuilder content = new StringBuilder();

            java.util.TreeSet<String> sortedKeys = new java.util.TreeSet<>(config.getKeys(true));
            
            for (String key : sortedKeys) {
                Object value = config.get(key);
                if (value != null && !config.isConfigurationSection(key)) {
                    content.append(key).append(":").append(value.toString()).append("\n");
                }
            }
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Migrates a setting from the specified old path in the config
     * to the new path.
     * @param oldPath the old path
     * @param newPath the new path
     */
    private void migrate(String oldPath, String newPath) {
        config.set(newPath, config.get(oldPath));
        config.set(oldPath, null);
    }

    /**
     * Migrates a setting from the specified old path in the config
     * to the new path in the localization config.
     * @param oldPath the old path
     * @param newPath the new path
     */
    private void migrateIntl(String oldPath, String newPath) {
        FileConfiguration intlConfig = localizationConfig.getConfig();
        intlConfig.set(newPath, config.get(oldPath));
        config.set(oldPath, null);
    }
}

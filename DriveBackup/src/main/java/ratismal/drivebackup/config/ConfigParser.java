package ratismal.drivebackup.config;

import java.nio.file.InvalidPathException;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.config.configSections.Advanced;
import ratismal.drivebackup.config.configSections.BackupList;
import ratismal.drivebackup.config.configSections.BackupMethods;
import ratismal.drivebackup.config.configSections.BackupScheduling;
import ratismal.drivebackup.config.configSections.BackupStorage;
import ratismal.drivebackup.config.configSections.ExternalBackups;
import ratismal.drivebackup.config.configSections.Messages;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.util.Logger;
import ratismal.drivebackup.util.MessageUtil;

public class ConfigParser {
    public static class Config {
        public static final int VERSION = 2;
        public final BackupStorage backupStorage;
        public final BackupScheduling backupScheduling;
        public final BackupList backupList;
        public final ExternalBackups externalBackups;
        public final BackupMethods backupMethods;
        public final Messages messages;
        public final Advanced advanced;
    
        private Config(
            BackupStorage backupStorage, 
            BackupScheduling backupScheduling, 
            BackupList backupList,
            ExternalBackups externalBackups,
            BackupMethods backupMethods,
            Messages messages,
            Advanced advanced
            ) {
    
            this.backupStorage = backupStorage;
            this.backupScheduling = backupScheduling;
            this.backupList = backupList;
            this.externalBackups = externalBackups;
            this.backupMethods = backupMethods;
            this.messages = messages;
            this.advanced = advanced;
        }
    }

    private FileConfiguration config;
    private static Config parsedConfig;

    /**
     * Creates an instance of the {@code Config} object
     * @param config A reference to the plugin's {@code config.yml}
     */
    public ConfigParser(FileConfiguration config) {
        this.config = config;
    }

    /**
     * Reloads the plugin's {@code config.yml}
     * @param config A reference to the plugin's {@code config.yml}
     */
    public void reload(FileConfiguration config, List<CommandSender> initiator) {
        this.config = config;
        reload(initiator);
    }

    /**
     * Gets the plugin's parsed config
     * @return the config
     */
    public static Config getConfig() {
        return parsedConfig;
    }

    /**
     * Reloads the plugin's {@code config.yml}
     */
    public void reload(List<CommandSender> initiators) {
        Logger logger = (input, placeholders) -> MessageUtil.Builder().mmText(input, placeholders).to(initiators).send();

        parsedConfig = new Config(
            BackupStorage.parse(config, logger),
            BackupScheduling.parse(config, logger),
            BackupList.parse(config, logger),
            ExternalBackups.parse(config, logger),
            BackupMethods.parse(config, logger),
            Messages.parse(config, logger),
            Advanced.parse(config, logger)
        );
    }

    @NotNull
    public static Config defaultConfig() {
        FileConfiguration config = DriveBackup.getInstance().getConfig();
        Logger logger = (input, placeholders) -> {};

        return new Config(
            BackupStorage.parse(config, logger),
            BackupScheduling.parse(config, logger),
            BackupList.parse(config, logger),
            ExternalBackups.parse(config, logger),
            BackupMethods.parse(config, logger),
            Messages.parse(config, logger),
            Advanced.parse(config, logger)
        );
    }

    @NotNull
    @Contract ("_ -> param1")
    public static String verifyPath(@NotNull String path) throws InvalidPathException {
        if (
            path.contains("\\")
        ) {
            throw new InvalidPathException(path, "Path must use the unix file separator, \"/\"");
        }
        return path;
    }
}

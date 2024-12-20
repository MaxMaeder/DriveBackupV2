package ratismal.drivebackupOld.config;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackupOld.config.configSections.Advanced;
import ratismal.drivebackupOld.config.configSections.BackupList;
import ratismal.drivebackupOld.config.configSections.BackupMethods;
import ratismal.drivebackupOld.config.configSections.BackupScheduling;
import ratismal.drivebackupOld.config.configSections.BackupStorage;
import ratismal.drivebackupOld.config.configSections.ExternalBackups;
import ratismal.drivebackupOld.config.configSections.Messages;
import ratismal.drivebackupOld.plugin.DriveBackup;

import java.nio.file.InvalidPathException;
import java.util.List;

@Deprecated
public class ConfigParser {
    @Deprecated
    public static final class Config {
        public static final int VERSION = 2;
        public final BackupStorage backupStorage;
        public final BackupScheduling backupScheduling;
        public final BackupList backupList;
        public final ExternalBackups externalBackups;
        public final BackupMethods backupMethods;
        public final Messages messages;
        public final Advanced advanced;
    
        @Contract (pure = true)
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
    @Contract (pure = true)
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
    @Contract (pure = true)
    public static Config getConfig() {
        return parsedConfig;
    }

    /**
     * Reloads the plugin's {@code config.yml}
     */
    public void reload(List<CommandSender> initiators) {
        parsedConfig = new Config(
            BackupStorage.parse(config),
            BackupScheduling.parse(config),
            BackupList.parse(config),
            ExternalBackups.parse(config),
            BackupMethods.parse(config),
            Messages.parse(config),
            Advanced.parse(config)
        );
    }

    @NotNull
    public static Config defaultConfig() {
        FileConfiguration config = DriveBackup.getInstance().getConfig();
        return new Config(
            BackupStorage.parse(config),
            BackupScheduling.parse(config),
            BackupList.parse(config),
            ExternalBackups.parse(config),
            BackupMethods.parse(config),
            Messages.parse(config),
            Advanced.parse(config)
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

package ratismal.drivebackup.config;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.zip.Deflater;
import java.util.zip.ZipOutputStream;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.configSections.Advanced;
import ratismal.drivebackup.config.configSections.BackupList;
import ratismal.drivebackup.config.configSections.BackupMethods;
import ratismal.drivebackup.config.configSections.BackupScheduling;
import ratismal.drivebackup.config.configSections.BackupStorage;
import ratismal.drivebackup.config.configSections.ExternalBackups;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.SchedulerUtil;

public class ConfigParser {
    public interface Logger {
        public void log(String message);
    }

    public static class Config {
        public final BackupStorage backupStorage;
        public final BackupScheduling backupScheduling;
        public final BackupList backupList;
        public final ExternalBackups externalBackups;
        public final BackupMethods backupMethods;
        public final Advanced advanced;
    
        private Config(
            BackupStorage backupStorage, 
            BackupScheduling backupScheduling, 
            BackupList backupList,
            ExternalBackups externalBackups,
            BackupMethods backupMethods,
            Advanced advanced
            ) {
    
            this.backupStorage = backupStorage;
            this.backupScheduling = backupScheduling;
            this.backupList = backupList;
            this.externalBackups = externalBackups;
            this.backupMethods = backupMethods;
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
    public void reload(FileConfiguration config, CommandSender initiator) {
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
    public void reload(CommandSender initiator) {
        Logger logger = message -> {
            MessageUtil.sendMessage(initiator, message);
            MessageUtil.sendConsoleMessage(message);
        };

        parsedConfig = new Config(
            BackupStorage.parse(config, logger),
            BackupScheduling.parse(config, logger),
            BackupList.parse(config, logger),
            ExternalBackups.parse(config, logger),
            BackupMethods.parse(config, logger),
            Advanced.parse(config, logger)
        );
    }
}

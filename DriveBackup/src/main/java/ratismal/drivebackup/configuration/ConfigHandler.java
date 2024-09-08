package ratismal.drivebackup.configuration;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.serialize.SerializationException;
import ratismal.drivebackup.handler.logging.PrefixedLogger;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ConfigHandler {
    
    private static final String CONFIG_FILE_NAME = "config";
    private static final String FAILED_TO_LOAD = "Failed to load config.yml";
    
    private final DriveBackupInstance instance;
    private ConfigurationObject configurationObject;
    private final PrefixedLogger logger;
    
    public ConfigHandler(@NotNull DriveBackupInstance instance) {
        this.instance = instance;
        logger = instance.getLoggingHandler().getPrefixedLogger("ConfigHandler");
        try {
            configurationObject = new ConfigurationObject(logger, instance.getDataDirectory(), CONFIG_FILE_NAME, instance, getDefaults());
            ConfigurationUtils.loadConfig(configurationObject);
        } catch (ConfigurateException e) {
            logger.error(FAILED_TO_LOAD, e);
            instance.disable();
        }
    }
    
    @Contract (pure = true)
    public PrefixedLogger getLogger() {
        return logger;
    }
    
    @Contract (pure = true)
    public ConfigurationObject getConfig() {
        return configurationObject;
    }
    
    public void generateNewConfig() {
        try {
            configurationObject = ConfigurationUtils.generateNewConfig(configurationObject);
        } catch (ConfigurateException e) {
            logger.error(FAILED_TO_LOAD, e);
            instance.disable();
        }
    }
    
    private static @NotNull CommentedConfigurationNode getDefaults() throws SerializationException {
        CommentedConfigurationNode defaults = CommentedConfigurationNode.root();
        defaults.node("version").set(2);
        defaults.node("version").comment("The version of the config file. Do not change this");
        defaults.node("delay").set(60);
        defaults.node("delay").comment("The delay between backups in minutes. Set to -1 to disable automatic backups");
        defaults.node("backup-thread-priority").set(1);
        defaults.node("backup-thread-priority").comment("The priority of the backup thread. 1 is the lowest priority, 10 is the highest");
        defaults.node("keep-count").set(20);
        defaults.node("keep-count").comment("The number of backups to keep on the remote storage");
        defaults.node("local-keep-count").set(0);
        defaults.node("local-keep-count").comment("The number of backups to keep locally. \n Set to 0 to disable keeping local backups \n"
                                                  + "If set to 0 it will still keep the last backup locally until its finished uploading to all remote storages");
        defaults.node("zip-compression").set(1);
        defaults.node("zip-compression").comment("The level of compression to use when zipping backups. \n"
                                                 + " 0 is no compression, 1 is the fastest, 9 is the best compression"
                                                 + " \n Higher levels of compression will take longer to compress");
        defaults.node("backups-require-players").set(Boolean.TRUE);
        defaults.node("backups-require-players").comment("Whether backups should only be made if there were players online since the last backup");
        defaults.node("disable-saving-during-backups").set(Boolean.TRUE);
        defaults.node("disable-saving-during-backups").comment("Whether to disable automatic saving while backups are being made");
        defaults.node("scheduled-backups").set(Boolean.FALSE);
        defaults.node("scheduled-backups").comment("Whether to enable scheduled backups");
        CommentedConfigurationNode backupScheduleList = defaults.node("backup-schedule-list");
        backupScheduleList.comment("The list of scheduled backups");
        List<String> days = new ArrayList<>(2);
        days.add("Sunday");
        days.add("Wednesday");
        backupScheduleList.node(0).node("days").setList(String.class, days);
        List<String> times = new ArrayList<>(2);
        times.add("6:00");
        times.add("18:00");
        backupScheduleList.node(0).node("time").set(times);
        backupScheduleList.node(1).node("days").set("everyday");
        backupScheduleList.node(1).node("time").set("2:30");
        CommentedConfigurationNode backupList = defaults.node("backup-list");
        backupList.node(0).node("glob").set("world*");
        backupList.node(0).node("format").set("Backup-%NAME-%FORMAT.zip");
        backupList.node(0).node("create").set(true);
        backupList.node(1).node("path").set("plugins");
        backupList.node(1).node("format").set("Backup-plugins-%FORMAT.zip");
        backupList.node(1).node("create").set(true);
        defaults.node("external--backup-list").set(Collections.emptyList());
        defaults.node("local-save-directory").set("backups");
        defaults.node("remote-save-directory").set("backups");
        defaults.node("googledrive").node("shared-drive-id").set("");
        defaults.node("googledrive").node("enable").set(Boolean.FALSE);
        defaults.node("onedrive").node("enable").set(Boolean.FALSE);
        defaults.node("dropbox").node("enable").set(Boolean.FALSE);
        CommentedConfigurationNode webdev = defaults.node("webdev");
        webdev.node("enable").set(Boolean.FALSE);
        webdev.node("hostname").set("https:///example.com/directory");
        webdev.node("username").set("username");
        webdev.node("password").set("password");
        CommentedConfigurationNode nextcloud = defaults.node("nextcloud");
        nextcloud.node("enable").set(Boolean.FALSE);
        nextcloud.node("hostname").set("https://example.com/remote.php/dav/files/user/v");
        nextcloud.node("username").set("username");
        nextcloud.node("password").set("password");
        nextcloud.node("chunk-size").set(20_000_000);
        CommentedConfigurationNode ftp = defaults.node("ftp");
        ftp.node("enable").set(Boolean.FALSE);
        ftp.node("hostname").set("ftp.example.com");
        ftp.node("port").set(21);
        ftp.node("sftp").set(Boolean.FALSE);
        ftp.node("ftps").set(Boolean.FALSE);
        ftp.node("username").set("username");
        ftp.node("password").set("password");
        ftp.node("sftp-public-key").set("");
        ftp.node("sftp-passphrase").set("");
        ftp.node("working-dir").set("");
        CommentedConfigurationNode messages = defaults.node("messages");
        messages.node("send-in-chat").set(Boolean.TRUE);
        messages.node("prefix").set("&6[&4DriveBackupV2&6] ");
        messages.node("default-color").set("&f");
        CommentedConfigurationNode advanced = defaults.node("advanced");
        advanced.node("metrics").set(Boolean.TRUE);
        advanced.node("update-check").set(Boolean.TRUE);
        advanced.node("suppress-errors").set(Boolean.FALSE);
        advanced.node("debug").set(Boolean.FALSE);
        advanced.node("date-language").set("en");
        advanced.node("date-timezone").set("-00:00");
        advanced.node("ftp-file-separator").set("/");
        return defaults;
    }
    
    public void save() throws ConfigurateException {
        ConfigurationUtils.saveConfig(configurationObject);
    }
    
    public void reload() throws ConfigurateException {
        ConfigurationUtils.loadConfig(configurationObject);
    }
    
}

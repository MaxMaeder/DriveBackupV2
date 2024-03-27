package ratismal.drivebackup.configuration;

import org.apache.commons.lang.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.serialize.SerializationException;

public final class ConfigMigrator {
    
    private static final int LATEST_VERSION = 2;
    private static final String newerVersionMessage = "Your config is a newer unknown version.";
    private static final String invalidConfigMessage = "Your config is invalid. Generating a new one.";
    
    @Contract (pure = true)
    private ConfigMigrator() {
    }
    
    public static boolean isMigrationRequired(@NotNull ConfigHandler config) {
        int version = config.getConfig().node("version").getInt();
        if (version == 0) {
            config.getLogger().error(invalidConfigMessage);
            config.generateNewConfig();
            return false;
        }
        if (LATEST_VERSION < version) {
            config.getLogger().error(newerVersionMessage);
            config.generateNewConfig();
        }
        return version < LATEST_VERSION;
    }
    
    public static void migrateConfig(@NotNull ConfigHandler configHandler, @NotNull LangConfigHandler langConfigHandler) throws ConfigurateException {
        int currentVersion = configHandler.getConfig().node("version").getInt();
        if (currentVersion == 1) {
            migrate1to2(configHandler, langConfigHandler);
        }
    }
    
    private static void migrate1to2(@NotNull ConfigHandler configHandler, @NotNull LangConfigHandler langConfigHandler) throws ConfigurateException {
        configHandler.getLogger().info("Automatically migrating config to version 2");
        CommentedConfigurationNode config = configHandler.getConfig();
        int backupThreadPriority = config.node("backup-thread-priority").getInt();
        if (backupThreadPriority < 1) {
            config.node("backup-thread-priority").set(1);
        }
        int zipCompression = config.node("zip-compression").getInt();
        if (zipCompression < 1) {
            config.node("zip-compression").set(1);
        }
        move(config, "dir", "local-save-directory");
        move(config, "destination", "remote-save-directory");
        if (config.node("schedule-timezone").virtual() || ObjectUtils.equals(config.node("schedule-timezone").getString(), "-00:00")) {
            move(config, "backup-format-timezone", "advanced.date-timezone");
            config.node("schedule-timezone").set(null);
        } else {
            move(config, "schedule-timezone", "advanced.date-timezone");
            config.node("backup-format-timezone").set(null);
        }
        if (config.node("googledrive.shared-drive-id").virtual()) {
            config.node("googledrive.shared-drive-id").set("");
        }
        move(config, "advanced.message-prefix", "messages.prefix");
        move(config, "advanced.default-message-color", "messages.default-color");
        
        //lang config
        CommentedConfigurationNode langConfig = langConfigHandler.getConfig();
        moveLang(langConfig, config, "messages.no-perm", "no-perm");
        moveLang(langConfig, config, "messages.backup-start", "backup-start");
        moveLang(langConfig, config, "messages.backup-complete", "backup-complete");
        moveLang(langConfig, config, "messages.next-backup", "next-backup");
        moveLang(langConfig, config, "messages.next-schedule-backup", "next-schedule-backup");
        moveLang(langConfig, config, "messages.next-schedule-backup-format", "next-schedule-backup-format");
        moveLang(langConfig, config, "messages.auto-backups-disabled", "auto-backups-disabled");
        
        config.node("version").set(2);
        configHandler.save();
        langConfigHandler.save();
    }
    
    private static void move(@NotNull CommentedConfigurationNode config, String oldPath, String newPath) throws SerializationException {
        if (config.node(oldPath).virtual()) {
            return;
        }
        config.node(newPath).set(config.node(oldPath).getString());
        config.node(oldPath).set(null);
    }
    
    private static void moveLang(@NotNull CommentedConfigurationNode langConfig, @NotNull CommentedConfigurationNode config, String oldPath, String newPath) throws SerializationException {
        if (config.node(oldPath).virtual()) {
            return;
        }
        langConfig.node(newPath).set(config.node(oldPath).getString());
        config.node(oldPath).set(null);
    }
}

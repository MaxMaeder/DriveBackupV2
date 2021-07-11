package ratismal.drivebackup.plugin;

import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import ratismal.drivebackup.config.ConfigMigrator;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.Localization;
import ratismal.drivebackup.config.Permissions;
import ratismal.drivebackup.handler.CommandTabComplete;
import ratismal.drivebackup.handler.PlayerListener;
import ratismal.drivebackup.handler.commandHandler.CommandHandler;
import ratismal.drivebackup.plugin.updater.UpdateChecker;
import ratismal.drivebackup.plugin.updater.Updater;
import ratismal.drivebackup.util.CustomConfig;
import ratismal.drivebackup.util.MessageUtil;

public class DriveBackup extends JavaPlugin {

    private static DriveBackup plugin;

    private static ConfigParser config;

    private static CustomConfig localizationConfig;
    private static Localization localization;

    public static Updater updater;

    /**
     * Global instance of Adventure audience
     */
    public static BukkitAudiences adventure;

    /**
     * What to do when plugin is enabled (init)
     */
    public void onEnable() {
        plugin = this;

        saveDefaultConfig();

        config = new ConfigParser(getConfig());
        config.reload(Permissions.getPlayersWithPerm(Permissions.RELOAD_CONFIG));

        localizationConfig = new CustomConfig("intl.yml");
        localizationConfig.saveDefaultConfig();
        localization = new Localization(localizationConfig.getConfig());

        getCommand(CommandHandler.CHAT_KEYWORD).setTabCompleter(new CommandTabComplete(plugin));
        getCommand(CommandHandler.CHAT_KEYWORD).setExecutor(new CommandHandler(plugin));
        
        DriveBackup.adventure = BukkitAudiences.create(plugin);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(), plugin);

        Scheduler.startBackupThread();

        BstatsMetrics.initMetrics();

        updater = new Updater(plugin, this.getFile());
        UpdateChecker.updateCheck();
    }

    /**
     * What to do when plugin is disabled
     */
    public void onDisable() {
        MessageUtil.sendConsoleMessage("Stopping plugin!");
    }

    public void saveIntlConfig() {
        localizationConfig.saveConfig();
    }

    /**
     * Gets an instance of the plugin
     *
     * @return DriveBackup plugin
     */
    public static DriveBackup getInstance() {
        return plugin;
    }

    /**
     * Reloads config
     */
    public static void reloadLocalConfig() {
        Scheduler.stopBackupThread();

        List<CommandSender> players = Permissions.getPlayersWithPerm(Permissions.RELOAD_CONFIG);
        
        getInstance().reloadConfig();
        FileConfiguration configFile = getInstance().getConfig();
        
        localizationConfig.reloadConfig();
        FileConfiguration localizationFile = localizationConfig.getConfig();

        ConfigMigrator configMigrator = new ConfigMigrator(configFile, localizationFile, players);
        configMigrator.migrate();

        config.reload(configFile, players);
        localization.reload(localizationFile);

        Scheduler.startBackupThread();
    }
}

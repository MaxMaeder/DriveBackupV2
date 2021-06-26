package ratismal.drivebackup.plugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.Permissions;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.handler.CommandTabComplete;
import ratismal.drivebackup.handler.PlayerListener;
import ratismal.drivebackup.handler.commandHandler.CommandHandler;
import ratismal.drivebackup.util.MessageUtil;

public class DriveBackup extends JavaPlugin {

    private static DriveBackup plugin;
    private static ConfigParser config;

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

        reloadConfig();
        config = new ConfigParser(getConfig());
        config.reload(Permissions.getPlayersWithPerm(Permissions.RELOAD_CONFIG));

        getCommand("drivebackup").setTabCompleter(new CommandTabComplete(plugin));
        getCommand("drivebackup").setExecutor(new CommandHandler(plugin));
        
        DriveBackup.adventure = BukkitAudiences.create(plugin);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(), plugin);

        Scheduler.startBackupThread();

        BstatsMetrics.initMetrics();
        UpdateChecker.updateCheck();
    }

    /**
     * What to do when plugin is disabled
     */
    public void onDisable() {
        MessageUtil.sendConsoleMessage("Stopping plugin!");
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

        getInstance().reloadConfig();
        FileConfiguration fileConfiguration = getInstance().getConfig();
        config.reload(fileConfiguration, Permissions.getPlayersWithPerm(Permissions.RELOAD_CONFIG));

        Scheduler.startBackupThread();
    }
}

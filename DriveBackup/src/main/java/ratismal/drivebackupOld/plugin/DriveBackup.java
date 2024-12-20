package ratismal.drivebackupOld.plugin;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import ratismal.drivebackup.constants.Permission;
import ratismal.drivebackup.platforms.bukkit.commandHandler.CommandHandler;
import ratismal.drivebackup.platforms.bukkit.commandHandler.CommandTabComplete;
import ratismal.drivebackupOld.config.ConfigMigrator;
import ratismal.drivebackupOld.config.ConfigParser;
import ratismal.drivebackupOld.config.Localization;
import ratismal.drivebackupOld.config.PermissionHandler;
import ratismal.drivebackupOld.plugin.updater.UpdateChecker;
import ratismal.drivebackupOld.plugin.updater.Updater;
import ratismal.drivebackupOld.util.CustomConfig;
import ratismal.drivebackupOld.util.MessageUtil;

import java.util.ArrayList;
import java.util.List;

import static ratismal.drivebackupOld.config.Localization.intl;

@Deprecated
public class DriveBackup extends JavaPlugin {

    private static DriveBackup plugin;

    private static ConfigParser config;

    private static CustomConfig localizationConfig;

    public static Updater updater;

    /**
     * Global instance of Adventure audience
     */
    public static BukkitAudiences adventure;

    /**
     * A list of players who are currently waiting to reply.
     */
    public static List<CommandSender> chatInputPlayers;

    /**
     * What to do when plugin is enabled (init)
     */
    @Override
    public void onEnable() {
        plugin = this;
        adventure = BukkitAudiences.create(plugin);
        chatInputPlayers = new ArrayList<>(1);
        List<CommandSender> configPlayers = PermissionHandler.getPlayersWithPerm(Permission.RELOAD_CONFIG);
        saveDefaultConfig();
        localizationConfig = new CustomConfig("intl.yml");
        localizationConfig.saveDefaultConfig();
        Localization.set(localizationConfig.getConfig());
        ConfigMigrator configMigrator = new ConfigMigrator(getConfig(), localizationConfig.getConfig(), configPlayers);
        configMigrator.migrate();
        config = new ConfigParser(getConfig());
        config.reload(configPlayers);
        MessageUtil.Builder()
            .to(configPlayers)
            .mmText(intl("config-loaded"))
            .send();
        getCommand(CommandHandler.CHAT_KEYWORD).setTabCompleter(new CommandTabComplete(null));
        getCommand(CommandHandler.CHAT_KEYWORD).setExecutor(new CommandHandler(null));
        PluginManager pm = getServer().getPluginManager();
        //pm.registerEvents(new BukkitPlayerListener(), plugin);
        //pm.registerEvents(new BukkitChatInputListener(), plugin);
        //Scheduler.startBackupThread();
        BstatsMetrics.initMetrics(null);
        updater = new Updater(getFile());
        UpdateChecker.updateCheck();
    }

    /**
     * What to do when plugin is disabled
     */
    @Override
    public void onDisable() {
        //Scheduler.stopBackupThread();
        MessageUtil.Builder().mmText(intl("plugin-stop")).send();
    }

    public static void saveIntlConfig() {
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
        //Scheduler.stopBackupThread();
        List<CommandSender> players = PermissionHandler.getPlayersWithPerm(Permission.RELOAD_CONFIG);
        getInstance().reloadConfig();
        FileConfiguration configFile = getInstance().getConfig();
        localizationConfig.reloadConfig();
        FileConfiguration localizationFile = localizationConfig.getConfig();
        config.reload(configFile, players);
        Localization.set(localizationFile);
        //Scheduler.startBackupThread();
    }
}

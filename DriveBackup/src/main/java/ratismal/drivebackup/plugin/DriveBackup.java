package ratismal.drivebackup.plugin;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import okhttp3.OkHttpClient;
import ratismal.drivebackup.config.ConfigMigrator;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.Localization;
import ratismal.drivebackup.config.Permissions;
import ratismal.drivebackup.handler.CommandTabComplete;
import ratismal.drivebackup.handler.commandHandler.CommandHandler;
import ratismal.drivebackup.handler.listeners.*;
import ratismal.drivebackup.plugin.updater.*;
import ratismal.drivebackup.util.CustomConfig;
import ratismal.drivebackup.util.HttpLogger;
import ratismal.drivebackup.util.MessageUtil;

import static ratismal.drivebackup.config.Localization.intl;

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
     * A list of players who are currently waiting to reply.
     */
    public static List<CommandSender> chatInputPlayers;

    /**
     * Global instance of OkHTTP client
     */
    public static OkHttpClient httpClient;

    /**
     * What to do when plugin is enabled (init)
     */
    public void onEnable() {
        plugin = this;

        DriveBackup.httpClient = new OkHttpClient.Builder()
            .addInterceptor(new HttpLogger())
            .build();
        DriveBackup.adventure = BukkitAudiences.create(plugin);
        DriveBackup.chatInputPlayers = new ArrayList<>();

        List<CommandSender> configPlayers = Permissions.getPlayersWithPerm(Permissions.RELOAD_CONFIG);

        saveDefaultConfig();

        localizationConfig = new CustomConfig("intl.yml");
        localizationConfig.saveDefaultConfig();

        localization = new Localization(localizationConfig.getConfig());

        ConfigMigrator configMigrator = new ConfigMigrator(getConfig(), localizationConfig.getConfig(), configPlayers);
        configMigrator.migrate();

        config = new ConfigParser(getConfig());
        config.reload(Permissions.getPlayersWithPerm(Permissions.RELOAD_CONFIG));

        MessageUtil.Builder()
            .to(configPlayers)
            .mmText(intl("config-loaded"))
            .send();

        getCommand(CommandHandler.CHAT_KEYWORD).setTabCompleter(new CommandTabComplete());
        getCommand(CommandHandler.CHAT_KEYWORD).setExecutor(new CommandHandler());

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(), plugin);
        pm.registerEvents(new ChatInputListener(), plugin);

        Scheduler.startBackupThread();

        BstatsMetrics.initMetrics();

        updater = new Updater(this.getFile());
        UpdateChecker.updateCheck();
    }

    /**
     * What to do when plugin is disabled
     */
    public void onDisable() {
        Scheduler.stopBackupThread();

        MessageUtil.Builder().mmText(intl("plugin-stop")).send();
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

        config.reload(configFile, players);
        localization.reload(localizationFile);

        Scheduler.startBackupThread();
    }
}

package ratismal.drivebackup.platforms.bukkit;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.spongepowered.configurate.ConfigurateException;
import ratismal.drivebackup.configuration.ConfigHandler;
import ratismal.drivebackup.configuration.ConfigMigrator;
import ratismal.drivebackup.configuration.LangConfigHandler;
import ratismal.drivebackup.handler.UpdateHandler;
import ratismal.drivebackup.handler.logging.LoggingHandler;
import ratismal.drivebackup.handler.messages.MessageHandler;
import ratismal.drivebackup.handler.permission.PermissionHandler;
import ratismal.drivebackup.handler.task.TaskHandler;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.plugin.BstatsMetrics;
import ratismal.drivebackup.plugin.Scheduler;
import ratismal.drivebackup.util.Version;

import java.io.File;
import java.util.ArrayList;

public final class BukkitPlugin extends JavaPlugin implements DriveBackupInstance {
    
    private UpdateHandler updateHandler;
    private PermissionHandler permissionHandler;
    private ConfigHandler configHandler;
    private TaskHandler taskHandler;
    private LoggingHandler loggingHandler;
    private MessageHandler messageHandler;
    private LangConfigHandler langConfigHandler;
    private static BukkitPlugin instance;
    private BukkitMessageHandler bukkitMessageHandler;
    private BukkitAudiences adventure;
    private ArrayList<CommandSender> chatInputPlayers;
    private Version currentVersion;
    
    
    @Override
    public PermissionHandler getPermissionHandler() {
        return permissionHandler;
    }
    
    @Override
    public File getJarFile() {
        return getFile();
    }
    
    @Override
    public File getDataDirectory() {
        return getDataFolder();
    }
    
    @Override
    public LoggingHandler getLoggingHandler() {
        return loggingHandler;
    }
    
    @Override
    public BukkitMessageHandler getMessageHandler() {
        return bukkitMessageHandler;
    }
    
    @Override
    public ConfigHandler getConfigHandler() {
        return configHandler;
    }
    
    @Override
    public LangConfigHandler getLangConfigHandler() {
        return langConfigHandler;
    }
    
    @Override
    public void disable() {
        getServer().getPluginManager().disablePlugin(this);
    }
    
    private static void setInstance(BukkitPlugin instance) {
        BukkitPlugin.instance = instance;
    }
    
    public static BukkitPlugin getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Plugin has not been initialized yet");
        }
        return instance;
    }
    
    @Override
    public void onLoad() {
        //do nothing
    }
    
    @Override
    public void onEnable() {
        setInstance(this);
        loggingHandler = new BukkitLoggingHandler(this, getLogger());
        configHandler = new ConfigHandler(this);
        langConfigHandler = new LangConfigHandler(this);
        if (ConfigMigrator.isMigrationRequired(configHandler)) {
            try {
                ConfigMigrator.migrateConfig(configHandler, langConfigHandler);
            } catch (ConfigurateException e) {
                loggingHandler.error("Failed to migrate config", e);
                disable();
            }
        }
        adventure = BukkitAudiences.create(this);
        bukkitMessageHandler = new BukkitMessageHandler(this);
        chatInputPlayers = new ArrayList<>(1);
        permissionHandler = new BukkitPermissionHandler(getServer());
        
        
        //getCommand("drivebackup").setExecutor(new BukkitCommandHandler());
        //getCommand("drivebackup").setTabCompleter(new BukkitCommandTabComplete());
        PluginManager pm = getServer().getPluginManager();
        //pm.registerEvents(new BukkitChatListener(), this);
        //pm.registerEvents(new BukkitPlayerListener(), this);
        Scheduler.startBackupThread();
        BstatsMetrics.initMetrics();
        updateHandler = new UpdateHandler(this);
    }
    
    @Override
    public void onDisable() {
    }
    
    @Override
    public Version getCurrentVersion() {
        return Version.parse(getDescription().getVersion().split("-")[0]);
    }
    
    @Override
    public TaskHandler getTaskHandler() {
        return new BukkitTaskHandler(this);
    }
    
    public BukkitAudiences getAudiences() {
        return adventure;
    }
}

package ratismal.drivebackup.platforms.bukkit;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurateException;
import ratismal.drivebackup.configuration.ConfigHandler;
import ratismal.drivebackup.configuration.ConfigMigrator;
import ratismal.drivebackup.configuration.LangConfigHandler;
import ratismal.drivebackup.handler.debug.ServerInformation;
import ratismal.drivebackup.handler.logging.LoggingHandler;
import ratismal.drivebackup.handler.permission.PermissionHandler;
import ratismal.drivebackup.handler.player.PlayerHandler;
import ratismal.drivebackup.handler.task.TaskHandler;
import ratismal.drivebackup.handler.update.UpdateHandler;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.plugin.BstatsMetrics;
import ratismal.drivebackup.util.Version;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;

public final class BukkitPlugin extends JavaPlugin implements DriveBackupInstance {
    
    private UpdateHandler updateHandler;
    private PermissionHandler permissionHandler;
    private ConfigHandler configHandler;
    private TaskHandler taskHandler;
    private LoggingHandler loggingHandler;
    private LangConfigHandler langConfigHandler;
    private static BukkitPlugin instance;
    private BukkitMessageHandler bukkitMessageHandler;
    private BukkitAudiences adventure;
    private ArrayList<CommandSender> chatInputPlayers;
    private Version currentVersion;
    private BukkitPlayerHandler playerHandler;
    private final Collection<String> autoSaveWorlds = new ArrayList<>(3);
    
    
    @Contract (pure = true)
    @Override
    public PermissionHandler getPermissionHandler() {
        return permissionHandler;
    }
    
    @Override
    public File getJarFile() {
        return getFile();
    }
    
    @Contract (pure = true)
    @Override
    public File getDataDirectory() {
        return getDataFolder();
    }
    
    @Contract (pure = true)
    @Override
    public LoggingHandler getLoggingHandler() {
        return loggingHandler;
    }
    
    @Contract (pure = true)
    @Override
    public BukkitMessageHandler getMessageHandler() {
        return bukkitMessageHandler;
    }
    
    @Contract (pure = true)
    @Override
    public ConfigHandler getConfigHandler() {
        return configHandler;
    }
    
    @Contract (pure = true)
    @Override
    public LangConfigHandler getLangConfigHandler() {
        return langConfigHandler;
    }
    
    @Contract (pure = true)
    @Override
    public PlayerHandler getPlayerHandler() {
        return playerHandler;
    }
    
    @Contract (pure = true)
    @Override
    public UpdateHandler getUpdateHandler() {
        return updateHandler;
    }
    
    @Override
    public void disable() {
        getServer().getPluginManager().disablePlugin(this);
    }
    
    private static void setInstance(BukkitPlugin instance) {
        BukkitPlugin.instance = instance;
    }
    
    @Contract (pure = true)
    public static BukkitPlugin getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Plugin has not been initialized yet");
        }
        return instance;
    }
    
    @Contract (pure = true)
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
        playerHandler = new BukkitPlayerHandler(this);
        bukkitMessageHandler = new BukkitMessageHandler(this);
        chatInputPlayers = new ArrayList<>(1);
        permissionHandler = new BukkitPermissionHandler(getServer());
        taskHandler = new BukkitTaskHandler(this);
        
        
        //getCommand("drivebackup").setExecutor(new BukkitCommandHandler());
        //getCommand("drivebackup").setTabCompleter(new BukkitCommandTabComplete());
        PluginManager pm = getServer().getPluginManager();
        //pm.registerEvents(new BukkitChatListener(), this);
        //pm.registerEvents(new BukkitPlayerListener(), this);
        BstatsMetrics.initMetrics();
        updateHandler = new UpdateHandler(this);
    }
    
    @Contract (pure = true)
    @Override
    public void onDisable() {
    }
    
    @Override
    public @NotNull Version getCurrentVersion() {
        if (currentVersion == null) {
            currentVersion = Version.parse(getDescription().getVersion().split("-")[0]);
        }
        return currentVersion;
    }
    
    @Contract (pure = true)
    @Override
    public @NotNull TaskHandler getTaskHandler() {
        return taskHandler;
    }
    
    @Contract (pure = true)
    @Override
    public void preBackupAutoSave() throws InterruptedException, ExecutionException {
        Bukkit.getScheduler().callSyncMethod(this, () -> {
            for (World world : Bukkit.getWorlds()) {
                if (world.isAutoSave()) {
                    autoSaveWorlds.add(world.getName());
                    world.setAutoSave(false);
                }
            }
            return Boolean.TRUE;
        }).get();
    }
    
    @Contract (pure = true)
    @Override
    public void postBackupAutoSave() throws InterruptedException, ExecutionException {
        Bukkit.getScheduler().callSyncMethod(this, () -> {
            for (World world : Bukkit.getWorlds()) {
                String worldName = world.getName();
                if (autoSaveWorlds.contains(worldName)) {
                    world.setAutoSave(true);
                    autoSaveWorlds.remove(worldName);
                }
            }
            return Boolean.TRUE;
        }).get();
    }
    
    @Contract (pure = true)
    public BukkitAudiences getAudiences() {
        return adventure;
    }
    
    @Contract (pure = true)
    @Override
    public @NotNull ServerInformation getServerInfo() {
        return new BukkitServerInformation(this);
    }
    
}

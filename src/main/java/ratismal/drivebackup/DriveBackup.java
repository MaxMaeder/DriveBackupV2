package ratismal.drivebackup;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.mcstats.Metrics;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.handler.CommandHandler;
import ratismal.drivebackup.util.MessageUtil;

import java.io.IOException;

public class DriveBackup extends JavaPlugin {

    private static Config pluginconfig;
    private static DriveBackup plugin;

    public void onEnable() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        pluginconfig = new Config(getConfig());
        pluginconfig.reload();
        getCommand("drivebackup").setExecutor(new CommandHandler(this));
        plugin = this;

        if (Config.isMetrics()) {
            try {
                Metrics metrics = new Metrics(this);
                metrics.start();
                MessageUtil.sendConsoleMessage("Metrics started");
            } catch (IOException e) {
                MessageUtil.sendConsoleMessage("Metrics failed to start");
            }
        }
        startThread();

    }

    public void onDisable() {
        MessageUtil.sendConsoleMessage("Stopping plugin!");
    }

    public static DriveBackup getInstance() {
        return plugin;
    }

    public static void startThread() {
        if (Config.getBackupDelay()/60/20 != -1) {
            MessageUtil.sendConsoleMessage("Starting the backup thread for every " + Config.getBackupDelay() + " ticks.");
            BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
            scheduler.scheduleSyncRepeatingTask(getInstance(), new UploadThread(), Config.getBackupDelay(), Config.getBackupDelay());
        }
    }

    public static void reloadLocalConfig() {
        getInstance().reloadConfig();
        pluginconfig.reload(getInstance().getConfig());
    }
}

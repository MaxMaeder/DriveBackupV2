package ratismal.drivebackup;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.scheduler.BukkitScheduler;
import org.mcstats.Metrics;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.handler.CommandHandler;
import ratismal.drivebackup.util.MessageUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;

public class DriveBackup extends JavaPlugin {

    private Config pluginconfig;
    private static DriveBackup plugin;

    public void onEnable() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        this.pluginconfig = new Config(this, getConfig());
        pluginconfig.reload();
        getCommand("drivebackup").setExecutor(new CommandHandler(this, pluginconfig));
        this.plugin = this;

        if (Config.isMetrics()) {
            try {
                Metrics metrics = new Metrics(this);
                metrics.start();
                MessageUtil.sendConsoleMessage("Metrics started");
            } catch (IOException e) {
                MessageUtil.sendConsoleMessage("Metrics failed to start");
            }
        }

        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(this, new UploadThread(), Config.getBackupDelay(), Config.getBackupDelay());

    }

    public void onDisable() {
        MessageUtil.sendConsoleMessage("Stopping plugin!");
    }

    public static DriveBackup getInstance() {
        return plugin;
    }
}

package ratismal.drivebackup.util;

import java.util.concurrent.Callable;

import org.bukkit.Bukkit;

import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.plugin.DriveBackup;

public class ServerUtil {
    /**
     * Turns the server auto save on/off
     * @param autoSave whether to save automatically
     */
    public static void setAutoSave(boolean autoSave) {
        if (!ConfigParser.getConfig().backupStorage.disableSavingDuringBackups) {
            return;
        }

        try {
            Bukkit.getScheduler().callSyncMethod(DriveBackup.getInstance(), new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), autoSave ? "save-on" : "save-off");
                }
            }).get();
        } catch (Exception exception) { }
    }
}

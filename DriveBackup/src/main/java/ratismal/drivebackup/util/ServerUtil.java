package ratismal.drivebackup.util;

import org.bukkit.Bukkit;
import org.bukkit.World;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.plugin.DriveBackup;

import java.util.concurrent.CompletableFuture;

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
            CompletableFuture<Void> future = new CompletableFuture<>();
            DriveBackup.getFoliaLib().getScheduler().runNextTick((task) -> {
                for (World world : Bukkit.getWorlds()) {
                    world.setAutoSave(autoSave);
                }
                future.complete(null);
            });
            future.join();
        } catch (Exception ignored) {
        
        }
    }
}

package ratismal.drivebackup.handler;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.util.MessageUtil;

/**
 * Created by Ratismal on 2016-03-04.
 */

public class PlayerListener implements Listener {

    public static boolean doBackups = false;

    public PlayerListener() {
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!doBackups) {
            MessageUtil.sendConsoleMessage("Enabling automatic backups.");
            doBackups = true;
        }
    }

}

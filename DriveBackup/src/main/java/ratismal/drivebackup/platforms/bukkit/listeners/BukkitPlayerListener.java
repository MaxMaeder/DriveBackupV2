package ratismal.drivebackup.platforms.bukkit.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.constants.Permission;
import ratismal.drivebackup.handler.permission.PermissionHandler;
import ratismal.drivebackup.objects.Player;
import ratismal.drivebackup.platforms.DriveBackupInstance;

public class BukkitPlayerListener implements Listener {
    
    private final DriveBackupInstance instance;
    
    public BukkitPlayerListener(DriveBackupInstance instance) {
        this.instance = instance;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!UploadThread.isAutoBackupsActive()) {
            instance.getMessageHandler().getLang("player-join-backup-enable").toConsole().send();
            UploadThread.setAutoBackupsActive(true);
        }
        Player player = new Player(event.getPlayer().getUniqueId());
        PermissionHandler permissionHandler = instance.getPermissionHandler();
        //if (UpdateChecker.isUpdateAvailable() && permissionHandler.hasPermission(player,Permission.LINK_ACCOUNTS)) {
        //    instance.getMessageHandler().getLang("player-join-update-available").to(player).send();
        //}
        if (!UploadThread.wasLastBackupSuccessful() && permissionHandler.hasPermission(player, Permission.BACKUP)) {
            instance.getMessageHandler().getLang("player-join-backup-failed").to(player).send();
        }
    }
    
}

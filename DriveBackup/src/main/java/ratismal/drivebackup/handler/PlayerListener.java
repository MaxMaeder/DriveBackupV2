package ratismal.drivebackup.handler;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.config.Permissions;
import ratismal.drivebackup.plugin.updater.UpdateChecker;
import ratismal.drivebackup.util.MessageUtil;

import static ratismal.drivebackup.config.Localization.intl;

/**
 * Created by Ratismal on 2016-03-04.
 */

public class PlayerListener implements Listener {

    private static boolean autoBackupsActive = false;

    public PlayerListener() {
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!autoBackupsActive) {
            MessageUtil.Builder().mmText(intl("player-join-backup-enable")).send();
            autoBackupsActive = true;
        }

        Player player = event.getPlayer();

        if (UpdateChecker.isUpdateAvailable() && player.hasPermission(Permissions.LINK_ACCOUNTS)) {

            MessageUtil.Builder()
                .mmText(intl("player-join-update-available"))
                .to(player)
                .toConsole(false)
                .send();
        }

        if (!UploadThread.wasLastBackupSuccessful() && player.hasPermission(Permissions.BACKUP)) {
            MessageUtil.Builder()
                .mmText(intl("player-join-backup-failed"))
                .to(player)
                .toConsole(false)
                .send();
        }
    }

    /**
     * Gets whether automatic updates should be active
     * @return whether automatic updates are active
     */
    public static boolean isAutoBackupsActive() {
        return autoBackupsActive;
    }

    /**
     * Gets whether automatic updates should be active
     * @param autoBackupsActiveValue whether automatic updates are active
     */
    public static void setAutoBackupsActive(boolean autoBackupsActiveValue) {
        autoBackupsActive = autoBackupsActiveValue;
    }

}

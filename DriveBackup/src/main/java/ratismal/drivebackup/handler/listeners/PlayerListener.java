package ratismal.drivebackup.handler.listeners;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.config.Localization;
import ratismal.drivebackup.plugin.updater.UpdateChecker;
import ratismal.drivebackup.util.MessageUtil;

public class PlayerListener implements Listener {
    private static boolean autoBackupsActive = false;

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!autoBackupsActive) {
            MessageUtil.Builder().mmText(Localization.intl("player-join-backup-enable")).send();
            PlayerListener.autoBackupsActive = true;
        }
        Player player = event.getPlayer();
        if (UpdateChecker.isUpdateAvailable() && player.hasPermission("drivebackup.linkAccounts")) {
            MessageUtil.Builder().mmText(Localization.intl("player-join-update-available")).to((CommandSender)player).toConsole(false).send();
        }
        if (!UploadThread.wasLastBackupSuccessful() && player.hasPermission("drivebackup.backup")) {
            MessageUtil.Builder().mmText(Localization.intl("player-join-backup-failed")).to((CommandSender)player).toConsole(false).send();
        }
    }

    public static boolean isAutoBackupsActive() {
        return autoBackupsActive;
    }

    public static void setAutoBackupsActive(boolean autoBackupsActiveValue) {
        autoBackupsActive = autoBackupsActiveValue;
    }
}

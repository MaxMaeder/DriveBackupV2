package ratismal.drivebackup.handler;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.util.MessageUtil;

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
            MessageUtil.sendConsoleMessage("Enabling automatic backups");
            autoBackupsActive = true;
        }

        if (DriveBackup.isUpdateAvailable() && event.getPlayer().hasPermission("drivebackup.linkAccounts")) {

            MessageUtil.sendMessage(event.getPlayer(),
                Component.text()
                    .append(Component.text("An update is available, get it here: ", NamedTextColor.DARK_AQUA))
                    .append(Component.text("http://bit.ly/2M14uVD", NamedTextColor.GOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("Go to URL")))
                        .clickEvent(ClickEvent.openUrl("http://bit.ly/2M14uVD")))
                    .build());
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

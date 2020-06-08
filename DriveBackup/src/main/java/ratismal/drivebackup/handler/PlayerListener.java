package ratismal.drivebackup.handler;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import net.kyori.text.TextComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;
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

            MessageUtil.sendMessage(event.getPlayer(), TextComponent.builder()
                .append(
                    TextComponent.of("An update is available, get it here: ")
                    .color(TextColor.DARK_AQUA)
                )
                .append(
                    TextComponent.of("http://bit.ly/2M14uVD")
                    .color(TextColor.GOLD)
                    .hoverEvent(HoverEvent.showText(TextComponent.of("Go to URL")))
                    .clickEvent(ClickEvent.openUrl("http://bit.ly/2M14uVD"))
                ).build());
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

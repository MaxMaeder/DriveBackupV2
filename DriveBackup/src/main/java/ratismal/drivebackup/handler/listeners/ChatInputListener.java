package ratismal.drivebackup.handler.listeners;

import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.constants.Initiator;
import ratismal.drivebackup.platforms.bukkit.BukkitPlugin;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.uploaders.UploadLogger;
import ratismal.drivebackup.uploaders.googledrive.GoogleDriveUploader;

@Deprecated
public class ChatInputListener implements Listener {

    @EventHandler
    public void onPlayerChat(@NotNull AsyncPlayerChatEvent event) {
        if (handleInput(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommand(@NotNull ServerCommandEvent event) {
        if (handleInput(event.getSender(), event.getCommand())) {
            event.setCancelled(true);
        }
    }
    
    /**
     * Handles input from a player
     * @param sender the player who sent the input
     * @param input the input
     * @return true if the input was handled, false otherwise
     */
    private static boolean handleInput(CommandSender sender, String input) {
        if (DriveBackup.chatInputPlayers.contains(sender)) {
            UploadLogger logger = new UploadLogger(BukkitPlugin.getInstance(), Initiator.CONSOLE);
            new GoogleDriveUploader(BukkitPlugin.getInstance(), logger).finalizeSharedDrives(sender, input);
            DriveBackup.chatInputPlayers.remove(sender);
            return true;
        }
        return false;
    }
}

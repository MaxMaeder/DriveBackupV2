package ratismal.drivebackup.handler.listeners;

import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.UploadThread.UploadLogger;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.uploaders.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.util.MessageUtil;

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
     * @return whether the input was handled
     */
    private static boolean handleInput(CommandSender sender, String input) {
        if (DriveBackup.chatInputPlayers.contains(sender)) {
            UploadLogger uploadLogger = new UploadLogger() {
                @Override
                public void log(String input, String... placeholders) {
                    MessageUtil.Builder().mmText(input, placeholders).to(sender).send();
                }
            };
            new GoogleDriveUploader(uploadLogger).finalizeSharedDrives(sender, input);
            DriveBackup.chatInputPlayers.remove(sender);
            return true;
        }
        return false;
    }
}

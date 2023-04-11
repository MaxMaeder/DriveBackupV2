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
        if (DriveBackup.chatInputPlayers.contains(event.getPlayer())) {
            UploadLogger uploadLogger = new UploadLogger() {
                @Override
                public void log(String input, String... placeholders) {
                    MessageUtil.Builder().mmText(input, placeholders).to(event.getPlayer()).send();
                }
            };
            event.setCancelled(true);
            new GoogleDriveUploader(uploadLogger).finalizeSharedDrives((CommandSender)event.getPlayer(), event.getMessage());
            DriveBackup.chatInputPlayers.remove(event.getPlayer());
        }
    }

    @EventHandler
    public void onCommand(@NotNull ServerCommandEvent event) {
        if (DriveBackup.chatInputPlayers.contains(event.getSender())) {
            UploadLogger uploadLogger = new UploadLogger() {
                @Override
                public void log(String input, String... placeholders) {
                    MessageUtil.Builder().mmText(input, placeholders).to(event.getSender()).send();
                }
            };
            event.setCancelled(true);
            new GoogleDriveUploader(uploadLogger).finalizeSharedDrives(event.getSender(), event.getCommand());
            DriveBackup.chatInputPlayers.remove(event.getSender());
        }
    }

}

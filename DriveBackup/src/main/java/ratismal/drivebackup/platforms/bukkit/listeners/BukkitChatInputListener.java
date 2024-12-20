package ratismal.drivebackup.platforms.bukkit.listeners;

import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.objects.Player;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.uploaders.UploadLogger;
import ratismal.drivebackup.uploaders.googledrive.GoogleDriveUploader;

public class BukkitChatInputListener implements Listener {
    
    private final DriveBackupInstance instance;
    
    public BukkitChatInputListener(DriveBackupInstance instance) {
        this.instance = instance;
    }

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
    private boolean handleInput(CommandSender sender, String input) {
        if (!(sender instanceof org.bukkit.entity.Player)) {
            return false;
        }
        Player player = new Player(((org.bukkit.entity.Player) sender).getUniqueId());
        if (instance.isChatInputPlayer(player)) {
            UploadLogger logger = new UploadLogger(instance, player);
            new GoogleDriveUploader(instance, logger).finalizeSharedDrives(player, input);
            instance.removeChatInputPlayer(player);
            return true;
        }
        return false;
    }
    
}

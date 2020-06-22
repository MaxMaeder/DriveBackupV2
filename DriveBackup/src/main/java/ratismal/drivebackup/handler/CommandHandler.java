package ratismal.drivebackup.handler;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import net.kyori.text.TextComponent;
import net.kyori.text.adapter.bukkit.TextAdapter;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;
import ratismal.drivebackup.DownloadThread;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.onedrive.OneDriveUploader;
import ratismal.drivebackup.util.MessageUtil;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class CommandHandler implements CommandExecutor {
    private DriveBackup plugin;

    /**
     * Creates an instance of the {@code CommandHandler} object
     * @param plugin a reference to the plugin
     */
    public CommandHandler(DriveBackup plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles commands sent by players
     * @param sender the player who sent command
     * @param command  the command that was sent
     * @param label the command alias that was used
     * @param args any arguments that followed the command
     * @return whether the command was handled
     */
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("drivebackup")) {
            if (args.length > 0) {
                switch (args[0].toLowerCase()) {
                    case "help":
                        sendHelpResources(sender);
                        break;
                    case "v":
                        sendVersion(sender);
                        break;
                    case "reloadconfig":
                        if (hasPerm(sender, "drivebackup.reloadConfig")) {
                            sendReloadConfig(sender);
                        }
                        break;
                    case "linkaccount":
                        if (args.length < 2) {
                            sendHelp(sender);
                            break;
                        } 

                    	if (hasPerm(sender, "drivebackup.linkAccounts")) {
                    		switch (args[1].toLowerCase()) {
                                case "googledrive":
                                    try {
                                        GoogleDriveUploader.authenticateUser(plugin, sender);
                                    } catch (Exception e) {
                                        MessageUtil.sendMessage(sender, "Failed to link your Google Drive account");
                                    
                                        MessageUtil.sendConsoleException(e);
                                    }
                                    break;
                      		    case "onedrive":
                      			    try {
                      				    OneDriveUploader.authenticateUser(plugin, sender);
                      			    } catch (Exception e) {
                      				    MessageUtil.sendMessage(sender, "Failed to link your OneDrive account");
                      				
                      				    MessageUtil.sendConsoleException(e);
                      			    }
                      			    break;
                                default:
                                    sendHelp(sender);
                                    break;
                    		}
                    	}
                    	break;
                    case "backup":
                        if (hasPerm(sender, "drivebackup.backup")) {
                            MessageUtil.sendMessage(sender, "Forcing a backup");

                            Runnable t = new UploadThread(sender);
                            new Thread(t).start();
                        }
                        break;
                    case "restore":
                        if (hasPerm(sender, "drivebackup.restore")) {
                            new DownloadThread(plugin, sender, args).run();
                        }
                        break;
                    default:
                        sendHelp(sender);
                        break;
                }
                return true;
            } else {
                sendHelp(sender);
                return true;
            }
        }
        
        return false;
    }

    /**
     * Checks if the specified player has the specified permission
     * @param player the player
     * @param permission the permission
     * @return whether they have permissions
     */
    private boolean hasPerm(CommandSender player, String permission) {
        if (!player.hasPermission(permission)) {
            sendNoPerms(player);
            return false;
        }
        return true;
    }

    /**
     * Sends a list of commands and what they do to the specified player
     * @param player the player to send the message to
     */
    private void sendHelp(CommandSender player) {
        player.sendMessage(ChatColor.GOLD + "|=======" + ChatColor.DARK_RED + "DriveBackup" + ChatColor.GOLD + "=======|");
        player.sendMessage(ChatColor.GOLD + "/drivebackup" + ChatColor.DARK_AQUA + " - Displays this menu");
        player.sendMessage(ChatColor.GOLD + "/drivebackup help" + ChatColor.DARK_AQUA + " - Displays help resources");
        player.sendMessage(ChatColor.GOLD + "/drivebackup v" + ChatColor.DARK_AQUA + " - Displays the plugin version");
        player.sendMessage(ChatColor.GOLD + "/drivebackup linkaccount googledrive" + ChatColor.DARK_AQUA + " - Links your Google Drive account for backups");
        player.sendMessage(ChatColor.GOLD + "/drivebackup linkaccount onedrive" + ChatColor.DARK_AQUA + " - Links your OneDrive account for backups");
        player.sendMessage(ChatColor.GOLD + "/drivebackup reloadconfig" + ChatColor.DARK_AQUA + " - Reloads the config.yml");
        player.sendMessage(ChatColor.GOLD + "/drivebackup backup" + ChatColor.DARK_AQUA + " - Manually initiates a backup");
    }

    /**
     * Sends a list of links to help resources to the specified player
     * @param player the player to send the message to
     */
    private void sendHelpResources(CommandSender player) {
        player.sendMessage(ChatColor.GOLD + "|=======" + ChatColor.DARK_RED + "DriveBackup" + ChatColor.GOLD + "=======|");
        player.sendMessage(ChatColor.DARK_AQUA + "Need help? Check out these helpful resources!");
        TextAdapter.sendComponent(player, TextComponent.builder()
        .append(
            TextComponent.of("Wiki: ")
            .color(TextColor.DARK_AQUA)
        )
        .append(
            TextComponent.of("http://bit.ly/3dDdmwK")
            .color(TextColor.GOLD)
            .hoverEvent(HoverEvent.showText(TextComponent.of("Go to URL")))
            .clickEvent(ClickEvent.openUrl("http://bit.ly/3dDdmwK"))
        )
        .build());
        TextAdapter.sendComponent(player, TextComponent.builder()
        .append(
            TextComponent.of("Discord: ")
            .color(TextColor.DARK_AQUA)
        )
        .append(
            TextComponent.of("http://bit.ly/3f4VuuT")
            .color(TextColor.GOLD)
            .hoverEvent(HoverEvent.showText(TextComponent.of("Go to URL")))
            .clickEvent(ClickEvent.openUrl("http://bit.ly/3f4VuuT"))
        )
        .build());
    }

    /**
     * Sends a message with the current plugin version to the specified player
     * @param player the player to send the message to
     */
    private void sendVersion(CommandSender player) {
    	MessageUtil.sendMessage(player, "Currently running on version " + plugin.getDescription().getVersion());
    }

    /**
     * Tells the specifed player they don't have permissions to run a command
     * @param player the player to send the message to
     */
    private void sendNoPerms(CommandSender player) {
        String noPerms = Config.getNoPerms();
        noPerms = MessageUtil.translateMessageColors(noPerms);
        MessageUtil.sendMessage(player, noPerms);
    }

    /**
     * Reloads the plugin's {@code config.yml}, then tells the specified player it has been reloaded
     *@param player the player to send the message to
     */
    private void sendReloadConfig(CommandSender sender) {
        DriveBackup.reloadLocalConfig();
        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.cancelTasks(DriveBackup.getInstance());
        DriveBackup.startThread();

        MessageUtil.sendMessage(sender, "Config reloaded!");
    }

}

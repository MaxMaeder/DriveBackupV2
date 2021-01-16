package ratismal.drivebackup.handler;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import net.kyori.text.TextComponent;
import net.kyori.text.adapter.bukkit.TextAdapter;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.TestThread;
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
        if (!(sender instanceof Player || sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender)) {
            MessageUtil.sendMessage(sender, "DriveBackupV2 only supports commands sent in-game and via the console");
            return true;
        }
        
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
                    case "status":
                        if (hasPerm(sender, "drivebackup.getBackupStatus")) {
                            MessageUtil.sendMessage(sender, UploadThread.getBackupStatus());
                        }
                        break;
                    case "nextbackup":
                        if (hasPerm(sender, "drivebackup.getNextBackup")) {
                            MessageUtil.sendMessage(sender, UploadThread.getNextAutoBackup());
                        }
                        break;
                    case "backup":
                        if (hasPerm(sender, "drivebackup.backup")) {
                            MessageUtil.sendMessage(sender, "Forcing a backup");

                            Runnable t = new UploadThread(sender);
                            new Thread(t).start();
                        }
                        break;
                    case "test":
                        if (hasPerm(sender, "drivebackup.backup")) {

                            Runnable t = new TestThread(sender, args);
                            new Thread(t).start();
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
        player.sendMessage(ChatColor.GOLD + "|======" + ChatColor.DARK_RED + "DriveBackupV2" + ChatColor.GOLD + "======|");
        TextAdapter.sendMessage(player, genCommandHelpMessage("/drivebackup", "Displays this menu"));
        TextAdapter.sendMessage(player, genCommandHelpMessage("/drivebackup help", "Displays help resources"));
        TextAdapter.sendMessage(player, genCommandHelpMessage("/drivebackup v", "Displays the plugin version"));
        TextAdapter.sendMessage(player, genCommandHelpMessage("/drivebackup linkaccount googledrive", "Links your Google Drive account for backups"));
        TextAdapter.sendMessage(player, genCommandHelpMessage("/drivebackup linkaccount onedrive", "Links your OneDrive account for backups"));
        TextAdapter.sendMessage(player, genCommandHelpMessage("/drivebackup reloadconfig", "Reloads the config.yml"));
        TextAdapter.sendMessage(player, genCommandHelpMessage("/drivebackup nextbackup", "Gets the time/date of the next auto backup"));
        TextAdapter.sendMessage(player, genCommandHelpMessage("/drivebackup status", "Gets the status of the running backup"));
        TextAdapter.sendMessage(player, genCommandHelpMessage("/drivebackup backup", "Manually initiates a backup"));
        TextAdapter.sendMessage(player, genCommandHelpMessage("/drivebackup test ftp", "Tests the connection to the (S)FTP server"));
    }

    /**
     * Generates a message describing what the specified command does using the specified description
     * <p>
     * The command in the generated message can be clicked on to be run
     * @param command the command
     * @param description what the command does
     * @return the message
     */
    private TextComponent genCommandHelpMessage(String command, String description) {
        return TextComponent.builder()
        .append(
            TextComponent.of(command)
            .color(TextColor.GOLD)
            .hoverEvent(HoverEvent.showText(TextComponent.of("Run command")))
            .clickEvent(ClickEvent.runCommand(command))
        )
        .append(
            TextComponent.of(" - " + description)
            .color(TextColor.DARK_AQUA)
        ).build();
    }

    /**
     * Sends a list of links to help resources to the specified player
     * @param player the player to send the message to
     */
    private void sendHelpResources(CommandSender player) {
        player.sendMessage(ChatColor.GOLD + "|======" + ChatColor.DARK_RED + "DriveBackupV2" + ChatColor.GOLD + "======|");
        player.sendMessage(ChatColor.DARK_AQUA + "Need help? Check out these helpful resources!");
        TextAdapter.sendMessage(player, TextComponent.builder()
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
        TextAdapter.sendMessage(player, TextComponent.builder()
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
     * Sends a message with the current plugin, java, and server software version to the specified player
     * @param player the player to send the message to
     */
    private void sendVersion(CommandSender player) {
        player.sendMessage(ChatColor.GOLD + "|======" + ChatColor.DARK_RED + "DriveBackupV2" + ChatColor.GOLD + "======|");
        player.sendMessage(ChatColor.DARK_AQUA + "Plugin version: " + ChatColor.GOLD + plugin.getDescription().getVersion());
        player.sendMessage(ChatColor.DARK_AQUA + "Java version: " + ChatColor.GOLD + System.getProperty("java.version"));
        player.sendMessage(ChatColor.DARK_AQUA + "Server software: " + ChatColor.GOLD + Bukkit.getName());
        player.sendMessage(ChatColor.DARK_AQUA + "Server software version: " + ChatColor.GOLD + Bukkit.getVersion());

        if (DriveBackup.isUpdateAvailable()) {
            player.sendMessage(ChatColor.GOLD + "Plugin update available!");
        }
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

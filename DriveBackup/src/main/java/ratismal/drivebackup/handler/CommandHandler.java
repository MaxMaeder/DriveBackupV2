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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.TestThread;
import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.dropbox.DropboxUploader;
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
                    case "debug":
                        if (hasPerm(sender, "drivebackup.reloadconfig")) {
                            MessageUtil.sendMessage(sender, "Generating Debug Log");

                            DebugCollector debugInfo = new DebugCollector(this.plugin);
                            String publishedUrl = debugInfo.publish(this.plugin);
                            MessageUtil.sendMessage(sender, "Debug URL: " + publishedUrl);
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
                                case "dropbox":
                                    try {
                                        DropboxUploader.authenticateUser(plugin, sender);
                                    } catch (Exception e) {
                                        MessageUtil.sendMessage(sender, "Failed to link your Dropbox account");

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
        DriveBackup.adventure.sender(player).sendMessage(genCommandHelpMessage("/drivebackup", "Displays this menu"));
        DriveBackup.adventure.sender(player).sendMessage(genCommandHelpMessage("/drivebackup help", "Displays help resources"));
        DriveBackup.adventure.sender(player).sendMessage(genCommandHelpMessage("/drivebackup v", "Displays the plugin version"));
        DriveBackup.adventure.sender(player).sendMessage(genCommandHelpMessage("/drivebackup linkaccount googledrive", "Links your Google Drive account for backups"));
        DriveBackup.adventure.sender(player).sendMessage(genCommandHelpMessage("/drivebackup linkaccount onedrive", "Links your OneDrive account for backups"));
        DriveBackup.adventure.sender(player).sendMessage(genCommandHelpMessage("/drivebackup linkaccount dropbox", "Links your Dropbox account for backups"));
        DriveBackup.adventure.sender(player).sendMessage(genCommandHelpMessage("/drivebackup reloadconfig", "Reloads the config.yml"));
        DriveBackup.adventure.sender(player).sendMessage(genCommandHelpMessage("/drivebackup debug", "Generates a debug log"));
        DriveBackup.adventure.sender(player).sendMessage(genCommandHelpMessage("/drivebackup nextbackup", "Gets the time/date of the next auto backup"));
        DriveBackup.adventure.sender(player).sendMessage(genCommandHelpMessage("/drivebackup status", "Gets the status of the running backup"));
        DriveBackup.adventure.sender(player).sendMessage(genCommandHelpMessage("/drivebackup backup", "Manually initiates a backup"));
        DriveBackup.adventure.sender(player).sendMessage(genCommandHelpMessage("/drivebackup test ftp", "Tests the connection to the (S)FTP server"));
    }

    /**
     * Generates a message describing what the specified command does using the specified description
     * <p>
     * The command in the generated message can be clicked on to be run
     * @param command the command
     * @param description what the command does
     * @return the message
     */
    private Component genCommandHelpMessage(String command, String description) {
        return Component.text()
            .append(
                Component.text(command)
                .color(NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Run command")))
                .clickEvent(ClickEvent.runCommand(command))
            )
            .append(
                Component.text(" - " + description)
                .color(NamedTextColor.DARK_AQUA)
            )
            .build();
    }

    /**
     * Sends a list of links to help resources to the specified player
     * @param player the player to send the message to
     */
    private void sendHelpResources(CommandSender player) {
        player.sendMessage(ChatColor.GOLD + "|======" + ChatColor.DARK_RED + "DriveBackupV2" + ChatColor.GOLD + "======|");
        player.sendMessage(ChatColor.DARK_AQUA + "Need help? Check out these helpful resources!");
        DriveBackup.adventure.sender(player).sendMessage(Component.text()
            .append(
                Component.text("Wiki: ")
                .color(NamedTextColor.DARK_AQUA)
            )
            .append(
                Component.text("http://bit.ly/3dDdmwK")
                .color(NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Go to URL")))
                .clickEvent(ClickEvent.openUrl("http://bit.ly/3dDdmwK"))
            )
            .build());
        DriveBackup.adventure.sender(player).sendMessage(Component.text()
            .append(
                Component.text("Discord: ")
                .color(NamedTextColor.DARK_AQUA)
            )
            .append(
                Component.text("http://bit.ly/3f4VuuT")
                .color(NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Go to URL")))
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

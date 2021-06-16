package ratismal.drivebackup.handler.commandHandler;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.plugin.UpdateChecker;
import ratismal.drivebackup.util.MessageUtil;

public class BasicCommands {
    /**
     * Sends a list of links to help resources to the specified player
     * @param player the player to send the message to
     */
    public static void sendDocs(CommandSender player) {
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
    public static void sendVersion(CommandSender player) {
        player.sendMessage(ChatColor.GOLD + "|======" + ChatColor.DARK_RED + "DriveBackupV2" + ChatColor.GOLD + "======|");
        player.sendMessage(ChatColor.DARK_AQUA + "Plugin version: " + ChatColor.GOLD + DriveBackup.getInstance().getDescription().getVersion());
        player.sendMessage(ChatColor.DARK_AQUA + "Java version: " + ChatColor.GOLD + System.getProperty("java.version"));
        player.sendMessage(ChatColor.DARK_AQUA + "Server software: " + ChatColor.GOLD + Bukkit.getName());
        player.sendMessage(ChatColor.DARK_AQUA + "Server software version: " + ChatColor.GOLD + Bukkit.getVersion());

        if (UpdateChecker.isUpdateAvailable()) {
            player.sendMessage(ChatColor.GOLD + "Plugin update available!");
        }
    }

    /**
     * Sends a list of commands and what they do to the specified player
     * @param player the player to send the message to
     */
    public static void sendHelp(CommandSender player) {
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
    private static Component genCommandHelpMessage(String command, String description) {
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
        ).build();
    }

    /**
     * Tells the specifed player they don't have permissions to run a command
     * @param player the player to send the message to
     */
    public static void sendNoPerms(CommandSender player) {
        String noPerms = Config.getNoPerms();
        noPerms = MessageUtil.translateMessageColors(noPerms);
        MessageUtil.sendMessage(player, noPerms);
    }
}
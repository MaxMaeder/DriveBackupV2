package ratismal.drivebackup.handler.commandHandler;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.config.configSections.BackupList.BackupListEntry;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.plugin.updater.UpdateChecker;
import ratismal.drivebackup.util.MessageUtil;

import static ratismal.drivebackup.config.Localization.intl;

import java.util.ArrayList;
import java.util.List;

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
     * Sends the configured list of backup locations to the specified player, and
     * a link to learn how to change them
     * @param player the player to send the message to
     */
    public static void sendBriefBackupList(CommandSender player) {
        Config config = ConfigParser.getConfig();

        Builder locationMessage = Component.text();
        List<String> backupLocations = new ArrayList<>();

        for (BackupListEntry entry : config.backupList.list) {
            backupLocations.add(entry.location.toString());
        }

        if (config.externalBackups.sources.length > 0) {
            backupLocations.add("some external backups");
        }

        locationMessage.append(
            Component.text("DriveBackupV2 will currently back up ")
            .color(NamedTextColor.DARK_AQUA));

        if (backupLocations.size() == 0) {
            locationMessage.append(
                Component.text("nothing")
                .color(NamedTextColor.DARK_AQUA));
        } else {
            for (int i = 0; i < backupLocations.size(); i++) {
    
                String linkWord = null;
                if (i == backupLocations.size() - 1) {
                    linkWord = " and ";
                } else if (i != 0) {
                    linkWord = ", ";
                }
    
                if (linkWord != null) {
                    locationMessage.append(
                        Component.text(linkWord)
                        .color(NamedTextColor.DARK_AQUA));
                }
    
                locationMessage.append(
                    Component.text(backupLocations.get(i))
                    .color(NamedTextColor.GOLD));
            }
        }

        MessageUtil.sendMessage(player, locationMessage.build());

        Component helpMessage = Component.text()
            .append(
                Component.text("Want to back up something else? See ")
                .color(NamedTextColor.DARK_AQUA))
            .append(
                Component.text("https://bit.ly/3xoHRAs")
                .color(NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Go to URL")))
                .clickEvent(ClickEvent.openUrl("https://bit.ly/3xoHRAs")))
            .build();

        MessageUtil.sendMessage(player, helpMessage);
    }

    /**
     * Tells the specifed player they don't have permissions to run a command
     * @param player the player to send the message to
     */
    public static void sendNoPerms(CommandSender player) {
        String noPerms = intl("no-perm");
        noPerms = MessageUtil.translateMessageColors(noPerms);
        MessageUtil.sendMessage(player, noPerms);
    }
}
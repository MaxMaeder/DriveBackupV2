package ratismal.drivebackup.handler.commandHandler;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.config.configSections.BackupList.BackupListEntry;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.plugin.updater.UpdateChecker;
import ratismal.drivebackup.util.MessageUtil;

import static ratismal.drivebackup.config.Localization.intl;

import java.util.ArrayList;
import java.util.List;

public final class BasicCommands {
    
    private BasicCommands() {
        throw new IllegalStateException("Utility class");
    }
    /**
     * Sends a list of links to help resources to the specified player.
     * @param player the player to send the message to
     */
    public static void sendDocs(CommandSender player) {
        MessageUtil.Builder()
            .addPrefix(false)
            .mmText(
                intl("drivebackup-docs-command"), 
                "header", intl("drivebackup-command-header"))
            .to(player)
            .toConsole(false)
            .send();
    }

    /**
     * Sends a message with the current plugin, java, and server software version to the specified player.
     * @param player the player to send the message to
     */
    public static void sendVersion(CommandSender player) {
        MessageUtil builder = MessageUtil.Builder()
            .addPrefix(false)
            .mmText(
                intl("drivebackup-version-command"), 
                "header", intl("drivebackup-command-header"),
                "plugin-version", DriveBackup.getInstance().getDescription().getVersion(),
                "java-version", System.getProperty("java.version"),
                "server-software", Bukkit.getName(),
                "server-version", Bukkit.getVersion()
                );
        if (UpdateChecker.isUpdateAvailable()) {
            builder.mmText(intl("drivebackup-version-update"));
        }
        builder
            .to(player)
            .toConsole(false)
            .send();
    }

    /**
     * Sends a list of commands and what they do to the specified player.
     * @param player the player to send the message to
     */
    public static void sendHelp(CommandSender player) {
        MessageUtil.Builder()
            .addPrefix(false)
            .mmText(
                intl("drivebackup-help-command"), 
                "header", intl("drivebackup-command-header"))
            .to(player)
            .toConsole(false)
            .send();
    }

    /**
     * Sends the configured list of backup locations to the specified player, and
     * a link to learn how to change them.
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
            backupLocations.add(intl("brief-backup-list-external-backups"));
        }
        if (backupLocations.isEmpty()) {
            locationMessage.append(
                MiniMessage.miniMessage().deserialize(intl("brief-backup-list-empty")));
        } else {
            for (int i = 0; i < backupLocations.size(); i++) {
                String linkWord = null;
                if (i == backupLocations.size() - 1) {
                    linkWord = intl("list-last-delimiter");
                } else if (i != 0) {
                    linkWord = intl("list-delimiter");
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
        MessageUtil.Builder().mmText(intl("brief-backup-list"), "list", locationMessage.build()).toConsole(false).to(player).send();
        MessageUtil.Builder().mmText(intl("brief-backup-list-help")).toConsole(false).to(player).send();
    }

    /**
     * Tells the specified player they don't have permissions to run a command.
     * @param player the player to send the message to
     */
    public static void sendNoPerms(CommandSender player) {
        MessageUtil.Builder().text(intl("no-perm")).to(player).toConsole(false).send();
    }
}

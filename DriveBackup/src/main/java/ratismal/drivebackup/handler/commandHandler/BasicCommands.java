package ratismal.drivebackup.handler.commandHandler;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent.Builder;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.Template;
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
        MessageUtil.Builder()
            .addPrefix(false)
            .mmText(intl("drivebackup-docs-command"), getHeader())
            .to(player)
            .toConsole(false)
            .send();
    }

    /**
     * Sends a message with the current plugin, java, and server software version to the specified player
     * @param player the player to send the message to
     */
    public static void sendVersion(CommandSender player) {
        MessageUtil builder = MessageUtil.Builder()
            .addPrefix(false)
            .mmText(
                intl("drivebackup-docs-command"), 
                getHeader(),
                Template.of("plugin-version", DriveBackup.getInstance().getDescription().getVersion()),
                Template.of("java-version", System.getProperty("java.version")),
                Template.of("server-software", Bukkit.getName()),
                Template.of("server-version", Bukkit.getVersion())
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
     * Sends a list of commands and what they do to the specified player
     * @param player the player to send the message to
     */
    public static void sendHelp(CommandSender player) {
        MessageUtil.Builder()
            .addPrefix(false)
            .mmText(intl("drivebackup-help-command"), getHeader())
            .to(player)
            .toConsole(false)
            .send();
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

        MessageUtil.Builder().text(locationMessage.build()).toConsole(false).to(player).send();

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

        MessageUtil.Builder().text(helpMessage).toConsole(false).to(player).send();
    }

    public static Template getHeader() {
        return Template.of("header", MiniMessage.get().parse(intl("drivebackup-command-header")));
    }

    /**
     * Tells the specified player they don't have permissions to run a command
     * @param player the player to send the message to
     */
    public static void sendNoPerms(CommandSender player) {
        MessageUtil.Builder().text(intl("no-perm")).to(player).toConsole(false).send();
    }
}
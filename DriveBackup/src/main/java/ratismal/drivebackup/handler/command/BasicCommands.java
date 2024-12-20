package ratismal.drivebackup.handler.command;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ratismal.drivebackup.handler.messages.MessageHandler;
import ratismal.drivebackup.objects.Player;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.util.HashMap;
import java.util.Map;

public final class BasicCommands {
    
    private BasicCommands() {
        throw new IllegalStateException("Utility class");
    }
    
    public static void send(MessageHandler handler, CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) {
            handler.toConsole().send();
        }
        if (sender instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
            handler.to(new Player(player.getUniqueId())).send();
        }
    }
    
    private static String header(DriveBackupInstance instance) {
        return instance.getMessageHandler().Builder().getLangString("drivebackup-command-header");
    }
    
    /**
     * Sends a list of links to help resources to the specified player.
     * @param player the player to send the message to
     */
    public static void sendDocs(DriveBackupInstance instance, CommandSender player) {
        MessageHandler handler = instance.getMessageHandler().Builder().getLang(
                "drivebackup-docs-command", "header", header(instance));
        send(handler, player);
    }

    /**
     * Sends a message with the current plugin, java and server software version to the specified player.
     * @param player the player to send the message to
     */
    public static void sendVersion(DriveBackupInstance instance, CommandSender player) {
        Map<String, String> placeholders = new HashMap <>();
        placeholders.put("plugin-version", instance.getCurrentVersion().toString());
        placeholders.put("java-version", System.getProperty("java.version"));
        placeholders.put("server-software", instance.getServerInfo().getServerType());
        placeholders.put("server-version", instance.getServerInfo().getServerVersion());
        MessageHandler handler = instance.getMessageHandler().Builder().getLang(
                "drivebackup-version-command", placeholders);
        send(handler, player);
        //if (UpdateChecker.isUpdateAvailable()) {
        //    builder.mmText(intl("drivebackup-version-update"));
        //}
    }

    /**
     * Sends a list of commands and what they do to the specified player.
     * @param player the player to send the message to
     */
    public static void sendHelp(DriveBackupInstance instance, CommandSender player) {
        MessageHandler handler = instance.getMessageHandler().Builder().getLang("drivebackup-help-command",
                "header", header(instance));
        send(handler, player);
    }

    /**
     * Tells the specified player they don't have permissions to run a command.
     * @param player the player to send the message to
     */
    public static void sendNoPerms(DriveBackupInstance instance, CommandSender player) {
        MessageHandler handler = instance.getMessageHandler().Builder().getLang("no-perm");
        send(handler, player);
    }
    
}

package ratismal.drivebackup.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mysql.cj.x.protobuf.MysqlxCrud.Collection;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.config.Config;

public class MessageUtil {

    /**
     * Sends the specified message to all logged in players and the console
     * @param message the message to send
     */
    public static void sendMessageToAllPlayers(String message) {
        Bukkit.getConsoleSender().sendMessage(prefixMessage(message));

        if (!Config.isSendMessagesInChat()) return;

        message = translateMessageColors(message);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(prefixMessage(message));
        }
    }

    /**
     * Sends the specified message to all logged in players with the spacified permission
     * @param message the message to send
     * @param permission the permission
     * @param sendToConsole whether to send the message to the server console as well
     */
    public static void sendMessageToPlayersWithPermission(String message, String permission, boolean sendToConsole) {
        sendMessageToPlayersWithPermission(message, permission, Collections.emptyList(), sendToConsole);
    }

    /**
     * Sends the specified message to all logged in players with the spacified permission and to players in the specified list
     * @param message the message to send
     * @param permission the permission
     * @param additionalPlayers additional players to send the message to
     * @param sendToConsole whether to send the message to the server console as well
     */
    public static void sendMessageToPlayersWithPermission(String message, String permission, List<CommandSender> additionalPlayers, boolean sendToConsole) {
        ArrayList<CommandSender> players = new ArrayList<>();
        players.addAll(additionalPlayers);

        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (player.hasPermission("drivebackup.linkAccounts") && !players.contains(player)) {
                players.add(player);
            }
        }

        for (CommandSender player : players) {
            if (player == null) {
                continue;
            }

            sendMessage(player, message);
        }

        if (sendToConsole) sendConsoleMessage(message);
    }

    /**
     * Sends the specified message to all logged in players with the spacified permission
     * @param message the message to send
     * @param permission the permission
     */
    public static void sendMessageToPlayersWithPermission(Component message, String permission) {
        sendMessageToPlayersWithPermission(message, permission, Collections.emptyList());
    }

    /**
     * Sends the specified message to all logged in players with the spacified permission and to players in the specified list
     * @param message the message to send
     * @param permission the permission
     * @param additionalPlayers additional players to send the message to
     */
    public static void sendMessageToPlayersWithPermission(Component message, String permission, List<CommandSender> additionalPlayers) {
        ArrayList<CommandSender> players = new ArrayList<>();
        players.addAll(additionalPlayers);

        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (player.hasPermission("drivebackup.linkAccounts") && !players.contains(player)) {
                players.add(player);
            }
        }

        for (CommandSender player : players) {
            if (player == null) {
                continue;
            }

            sendMessage(player, message);
        }
    }

    /**
     * Sends the specified message to the specified player
     * @param player the player to send the message to
     * @param message the message to send
     */
    public static void sendMessage(CommandSender player, String message) {
        player.sendMessage(prefixMessage(message));
    }

    /**
     * Sends the specified message to the specified player
     * @param player the player to send the message to
     * @param message the message to send
     */
    public static void sendMessage(CommandSender player, Component message) {
        DriveBackup.adventure.sender(player).sendMessage(prefixMessage(message));
    }

    /**
     * Sends the specified message to the server console
     * @param message the message to send
     */
    public static void sendConsoleMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(prefixMessage(message));
    }

    /**
     * Sends the stack trace corresponding to the specified exception to the
     * console, only if suppress errors is disabled
     * <p>
     * Whether suppress errors is enabled is specified by the user in the
     * {@code config.yml}
     * 
     * @param exception Exception to send the stack trace of
     */
    public static void sendConsoleException(Exception exception) {
        if (Config.isDebug()) {
            exception.printStackTrace();
        }
    }

    /**
     * Prefixes the specified message with the plugin name
     * @param message the message to prefix
     * @return the prefixed message
     */
    private static String prefixMessage(String message) {
        return translateMessageColors(Config.getMessagePrefix() + Config.getDefaultMessageColor()) + message;
    }

    /**
     * Prefixes the specified message with the plugin name
     * @param message the message to prefix
     * @return the prefixed message
     */
    private static Component prefixMessage(Component message) {
        return Component.text()
            .append(Component.text("[", NamedTextColor.GOLD))
            .append(Component.text("DriveBackupV2", NamedTextColor.DARK_RED))
            .append(Component.text("] ", NamedTextColor.GOLD))
            .append(message).build();
    }

    /**
     * Translates the color codes in the specified message to the type used internally
     * @param message the message to translate
     * @return the translated message
     */
    public static String translateMessageColors(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

}

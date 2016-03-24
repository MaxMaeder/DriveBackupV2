package ratismal.drivebackup.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MessageUtil {

    /**
     * Sends message to all players and console
     *
     * @param message Message to send
     */
    public static void sendMessageToAllPlayers(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(getMessage(message));
        }
        Bukkit.getConsoleSender().sendMessage(getMessage(message));
    }

    /**
     * Sends a message to a player
     *
     * @param sender  Player to send message to
     * @param message Message to send
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (null != sender) {
            sender.sendMessage(getMessage(message));
        }
    }

    /**
     * Sends a message to console
     *
     * @param message Message to send
     */
    public static void sendConsoleMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(getMessage(message));
    }

    /**
     * Processes a message
     *
     * @param message
     * @return
     */
    private static String getMessage(String message) {
        return "\2476[\2474DriveBackup\2476]\2473 " + message;
    }

    /**
     * Processes a general message
     *
     * @param process Message to process
     * @return Processed message
     */
    public static String processGeneral(String process) {

        process = ChatColor.translateAlternateColorCodes('&', process);

        return process;
    }

}

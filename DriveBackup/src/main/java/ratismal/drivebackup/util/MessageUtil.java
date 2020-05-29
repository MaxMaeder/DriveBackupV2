package ratismal.drivebackup.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import ratismal.drivebackup.config.Config;

public class MessageUtil {

    /**
     * Sends message to all players and console
     *
     * @param message Message to send
     */
    public static void sendMessageToAllPlayers(String message) {
        Bukkit.getConsoleSender().sendMessage(getMessage(message));

        if (!Config.isSendMessagesInChat()) return;

        message = processGeneral(message);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (Config.isPrefixChatMessages()) {
                p.sendMessage(getMessage(message));
            } else {
                p.sendMessage(ChatColor.DARK_AQUA + message);
            }
        }
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
     * Sends the stack trace corresponding to the specified exception to the console, only if suppress errors is disabled
     * <p>
     * Whether suppress errors is enabled is specified by the user in the {@code config.yml}
     * @param exception Exception to send the stack trace of
     */
    public static void sendConsoleException(Exception exception) {
    	if (Config.isDebug()) {
    		exception.printStackTrace();
    	}
    }
    
    /**
     * Processes a message
     *
     * @param message
     * @return
     */
    private static String getMessage(String message) {
        return "\2476[\2474DriveBackupV2\2476]\2473 " + message;
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

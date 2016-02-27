package ratismal.drivebackup.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MessageUtil {

    public static void sendMessageToAllPlayers(String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(getMessage(message));
        }
        Bukkit.getConsoleSender().sendMessage(getMessage(message));
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (null != sender) {
            sender.sendMessage(getMessage(message));
        }
    }

    public static void sendConsoleMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(getMessage(message));
    }

    private static String getMessage(String message){
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

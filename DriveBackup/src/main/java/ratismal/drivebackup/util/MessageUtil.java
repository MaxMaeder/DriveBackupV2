package ratismal.drivebackup.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.text.TextComponent;
import net.kyori.text.adapter.bukkit.TextAdapter;
import net.kyori.text.format.TextColor;
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
            if (Config.isPrefixChatMessages()) {
                p.sendMessage(prefixMessage(message));
            } else {
                p.sendMessage(ChatColor.DARK_AQUA + message);
            }
        }
    }

    /**
     * Sends the specified message to the specified CommandSender
     * @param commandSender the CommandSender to send the message to
     * @param message the message to send
     */
    public static void sendMessage(CommandSender commandSender, String message) {
        commandSender.sendMessage(prefixMessage(message));
    }

    /**
     * Sends the specified message to the specified CommandSender
     * @param commandSender the CommandSender to send the message to
     * @param message the message to send
     */
    public static void sendMessage(CommandSender commandSender, TextComponent message) {
        TextAdapter.sendComponent(commandSender, prefixMessage(message));
    }

    /**
     * Sends the specified message to the server console
     * @param message the message to send
     */
    public static void sendConsoleMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(prefixMessage(message));
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
     * Prefixes the specified message with the plugin name
     * @param message the message to prefix
     * @return the prefixed message
     */
    private static String prefixMessage(String message) {
        return "\2476[\2474DriveBackupV2\2476]\2473 " + message;
    }

    /**
     * Prefixes the specified message with the plugin name
     * @param message the message to prefix
     * @return the prefixed message
     */
    private static TextComponent prefixMessage(TextComponent message) {
        return TextComponent.builder()
                .append(
                    TextComponent.of("[")
                    .color(TextColor.GOLD)
                )
                .append(
                    TextComponent.of("DriveBackupV2")
                    .color(TextColor.DARK_RED)
                )
                .append(
                    TextComponent.of("] "))
                    .color(TextColor.GOLD)
                .append(message)
                .build();
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

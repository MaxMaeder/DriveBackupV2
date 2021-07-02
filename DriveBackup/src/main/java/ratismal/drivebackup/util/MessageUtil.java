package ratismal.drivebackup.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.configSections.Messages;
import ratismal.drivebackup.plugin.DriveBackup;

public class MessageUtil {

    private Set<CommandSender> recipients = new HashSet<CommandSender>();
    private List<Component> message = new ArrayList<Component>();
    private Boolean sendToConsole = true;

    public MessageUtil() {
    }

    public MessageUtil(String text) {
        message.add(Component.text(text, NamedTextColor.DARK_AQUA));
    }

    public MessageUtil(TextComponent component) {
        message.add(component);
    }

    /**
     * Adds a player to the list of recipients
     * @param player the player to be added to the recipients
     * @return the calling MessageUtil's instance
     */
    public MessageUtil to(CommandSender player) {
        recipients.add(player);
        return this;
    }

    /**
     * Adds a list of players to the list of recipients
     * @param players the list of players to be added to the recipients
     * @return the calling MessageUtil's instance
     */
    public MessageUtil to(List<CommandSender> players) {
        for (CommandSender player : players) {
          recipients.add(player);
        }
        return this;
    }

    public MessageUtil toPerm(String permission) {
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
          if (player.hasPermission("drivebackup.linkAccounts") && !recipients.contains(player)) {
              recipients.add(player);
          }
        }
        return this;
    }

    public MessageUtil toConsole(Boolean value) {
        sendToConsole = value;
        return this;
    }

    /**
     * Adds all online players to the list of recipients
     * @return the calling MessageUtil's instance
     */
    public MessageUtil all() {
        for (Player p : Bukkit.getOnlinePlayers()) {
          recipients.add(p);
        }
        return this;
    }

    /**
     * Sends the message to the recipients
     */
    public void send() {
        TextComponent.Builder builtComponent = Component.text();
        for (Component component : message) {
            builtComponent.append(component);
        }
        String messageString = translateMessageColors(prefixMessage(builtComponent.content()));

        if (sendToConsole) Bukkit.getConsoleSender().sendMessage(messageString);

        if (!ConfigParser.getConfig().messages.sendInChat) return;

        for (CommandSender player : recipients) {
            if (player == null) {
                continue;
            }
                
            player.sendMessage(messageString);
        }
    }

    /**
     * Sends the stack trace corresponding to the specified exception to the console, only if suppress errors is disabled
     * <p>
     * Whether suppress errors is enabled is specified by the user in the {@code config.yml}
     * @param exception Exception to send the stack trace of
     */
    public static void sendConsoleException(Exception exception) {
    	if (ConfigParser.getConfig().advanced.suppressErrors) {
    		exception.printStackTrace();
    	}
    }
    
    /**
     * Prefixes the specified message with the plugin name
     * @param message the message to prefix
     * @return the prefixed message
     */
    private static String prefixMessage(String message) {
        Messages messages;
        try {
            messages = ConfigParser.getConfig().messages;
        } catch (Exception exception) {
            messages = Messages.defaultConfig();
        }

        return messages.prefix + messages.defaultColor + message;
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

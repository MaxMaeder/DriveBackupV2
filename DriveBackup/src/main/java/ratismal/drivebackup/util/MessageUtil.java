package ratismal.drivebackup.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.ObjectUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.plugin.DriveBackup;

public class MessageUtil {

    private Set<CommandSender> recipients = new HashSet<CommandSender>();
    private List<TextComponent> message = new ArrayList<TextComponent>();
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
        Component builtComponent = prefixMessage(Component.join(Component.text(" "), message));
        String consoleString = translateMessageColors(LegacyComponentSerializer.legacyAmpersand().serialize(builtComponent));

        if (sendToConsole) Bukkit.getConsoleSender().sendMessage(consoleString);

        Config config = (Config) ObjectUtils.defaultIfNull(ConfigParser.getConfig(), ConfigParser.defaultConfig());
        if (!config.messages.sendInChat) return;

        for (CommandSender player : recipients) {
            if (player == null) {
                continue;
            }

            DriveBackup.adventure.sender(player).sendMessage(builtComponent);
        }
    }

    /**
     * Sends the stack trace corresponding to the specified exception to the console, only if suppress errors is disabled
     * <p>
     * Whether suppress errors is enabled is specified by the user in the {@code config.yml}
     * @param exception Exception to send the stack trace of
     */
    public static void sendConsoleException(Exception exception) {
        Config config = (Config) ObjectUtils.defaultIfNull(ConfigParser.getConfig(), ConfigParser.defaultConfig());
    	if (!config.advanced.suppressErrors) {
    		exception.printStackTrace();
    	}
    }
    
    /**
     * Prefixes the specified message with the plugin name
     * @param message the message to prefix
     * @return the prefixed message
     */
    private static String prefixMessage(String message) {
        Config config = (Config) ObjectUtils.defaultIfNull(ConfigParser.getConfig(), ConfigParser.defaultConfig());

        return config.messages.prefix + config.messages.defaultColor + message;
    }

    private static Component prefixMessage(Component message) {
        Config config = (Config) ObjectUtils.defaultIfNull(ConfigParser.getConfig(), ConfigParser.defaultConfig());

        return Component.text(config.messages.prefix).append(message);
    }

    /**
     * Translates the color codes in the specified message to the type used internally
     * @param message the message to translate
     * @return the translated message
     */
    public static String translateMessageColors(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static void openBook(ItemStack book, Player[] players) {
        for (Player player : players) {
            player.openBook(book);
        }
    }

}

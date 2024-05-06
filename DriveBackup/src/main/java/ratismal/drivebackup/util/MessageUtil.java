package ratismal.drivebackup.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.Builder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.config.PermissionHandler;
import ratismal.drivebackup.constants.Permission;
import ratismal.drivebackup.plugin.DriveBackup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MessageUtil {

    private boolean addPrefix = true;
    private final List<Component> message = new ArrayList<>();
    private final Set<CommandSender> recipients = new HashSet<>();
    private Boolean sendToConsole = true;
    

    @NotNull
    @Contract (" -> new")
    public static MessageUtil Builder() {
        return new MessageUtil();
    }

    public MessageUtil() {
    }

    public MessageUtil addPrefix(boolean prefix) {
        addPrefix = prefix;
        return this;
    }
    
    public MessageUtil text(String text) {
        message.add(Component.text(text, getColor()));
        return this;
    }

    public MessageUtil emText(String text) {
        message.add(Component.text(text, NamedTextColor.GOLD));
        return this;
    }

    /**
     * Parses & add MiniMessage formatted text to the message
     * @param text the MiniMessage text
     * @return the calling MessageUtil's instance
     */
    public MessageUtil mmText(String text) {
        message.add(MiniMessage.miniMessage().deserialize("<color:" + getColor().asHexString() + ">" + text));
        return this;
    }

    /**
     * Parses & add MiniMessage formatted text to the message
     * @param text the MiniMessage text
     * @param placeholders optional MiniMessage placeholders
     * @return the calling MessageUtil's instance
     */
    public MessageUtil mmText(String text, @NotNull String... placeholders) {
        Builder builder = TagResolver.builder();
        for (int i = 0; i < placeholders.length; i += 2) {
            builder.resolver(Placeholder.parsed(placeholders[i], placeholders[i + 1]));
        }
        message.add(MiniMessage.miniMessage().deserialize("<color:" + getColor().asHexString() + ">" + text, builder.build()));
        return this;
    }

    /**
     * Parses & add MiniMessage formatted text to the message
     * @param text the MiniMessage text
     * @param title what to replace
     * @param content what to replace with
     * @return the calling MessageUtil's instance
     */
    public MessageUtil mmText(String text, String title, Component content) {
        message.add(MiniMessage.miniMessage().deserialize("<color:" + getColor().asHexString() + ">" + text, TagResolver.resolver(Placeholder.component(title, content))));
        return this;
    }

    public MessageUtil text(Component component) {
        message.add(component);
        return this;
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
    public MessageUtil to(@NotNull List<CommandSender> players) {
        for (CommandSender player : players) {
            recipients.add(player);
        }
        return this;
    }

    /**
     * Adds all online players with the specified permissions to the recipients.
     * @param permission the specified permission to be added to the recipients
     * @return the calling MessageUtil's instance
     */
    public MessageUtil toPerm(Permission permission) {
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (PermissionHandler.hasPerm(player, permission) && !recipients.contains(player)) {
                recipients.add(player);
            }
        }
        return this;
    }

    /**
     * Set whether or not if the message should be sent to the console.
     * @param value boolean
     * @return the calling MessageUtil's instance
     */
    public MessageUtil toConsole(boolean value) {
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
        JoinConfiguration separator = JoinConfiguration.separator(Component.text(" "));
        Component builtComponent = Component.join(separator, message);
        if (addPrefix) {
            builtComponent = prefixMessage(builtComponent);
        }
        if (sendToConsole) {
            recipients.add(Bukkit.getConsoleSender());
        }
        for (CommandSender player : recipients) {
            if (player == null || (!getConfig().messages.sendInChat && player instanceof Player)) {
                continue;
            }
            DriveBackup.adventure.sender(player).sendMessage(builtComponent);
        }
    }

    /**
     * Sends the stack trace corresponding to the specified exception to the console,
     * only if suppress errors are disabled.
     * <p>
     * Whether suppress errors is enabled is specified by the user in the {@code config.yml}
     * @param exception Exception to send the stack trace of
     */
    public static void sendConsoleException(Exception exception) {
        if (!getConfig().advanced.suppressErrors) {
            exception.printStackTrace();
        }
    }
    
    /**
     * Prefixes the specified message with the plugin name
     * @param message the message to prefix
     * @return the prefixed message
     */
    @NotNull
    private static Component prefixMessage(Component message) {
        return Component.text(translateMessageColors(getConfig().messages.prefix)).append(message);
    }

    /**
     * Translates the color codes in the specified message to the type used internally.
     * @param message the message to translate
     * @return the translated message
     */
    @NotNull
    @Contract ("_ -> new")
    public static String translateMessageColors(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
    
    private static @NotNull Config getConfig() {
        Config config = ConfigParser.getConfig();
        if (config == null) {
            config = ConfigParser.defaultConfig();
        }
        return config;
    }
    
    private static @NotNull TextColor getColor() {
        TextColor color = LegacyComponentSerializer.legacyAmpersand().deserialize(getConfig().messages.defaultColor).color();
        if (color == null) {
            color = NamedTextColor.DARK_AQUA;
        }
        return color;
    }
}

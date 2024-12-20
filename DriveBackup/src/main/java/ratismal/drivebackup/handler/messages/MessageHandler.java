package ratismal.drivebackup.handler.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.ansi.ColorLevel;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.configuration.ConfigHandler;
import ratismal.drivebackup.configuration.LangConfigHandler;
import ratismal.drivebackup.constants.Permission;
import ratismal.drivebackup.handler.permission.PermissionHandler;
import ratismal.drivebackup.objects.Player;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class MessageHandler {
    
    private static final ANSIComponentSerializer ANSI_COMPONENT_SERIALIZER =
            ANSIComponentSerializer.builder().colorLevel(ColorLevel.TRUE_COLOR).build();
    private static final JoinConfiguration JOIN_CONFIGURATION = JoinConfiguration.separator(Component.text(""));
    protected final List<Component> message = new ArrayList<>(5);
    protected final ArrayList<Player> recipients = new ArrayList<>(5);
    protected final DriveBackupInstance instance;
    private final PermissionHandler permissionHandler;
    private final ConfigHandler configHandler;
    private final LangConfigHandler langConfigHandler;
    private ConsoleLogLevel consoleLogLevel = ConsoleLogLevel.INFO;
    private boolean sendToConsole = true;
    private boolean addPrefix = true;
    private Throwable throwable;
    
    protected MessageHandler(@NotNull DriveBackupInstance instance) {
        this.instance = instance;
        permissionHandler = instance.getPermissionHandler();
        configHandler = instance.getConfigHandler();
        langConfigHandler = instance.getLangConfigHandler();
    }
    
    /**
     * Reset the message handler
     *
     * @return The MessageHandler
     */
    public MessageHandler reset() {
        message.clear();
        recipients.clear();
        addPrefix = true;
        sendToConsole = true;
        consoleLogLevel = ConsoleLogLevel.INFO;
        return this;
    }
    
    private TextColor getColor() {
        String colorString = configHandler.getConfig().getValue("messages", "default-color").getString();
        TextColor color = null;
        if (colorString != null) {
            color = LegacyComponentSerializer.legacyAmpersand().deserialize(colorString).color();
        }
        if (color == null) {
            color = NamedTextColor.DARK_AQUA;
        }
        return color;
    }
    
    private @NotNull String getMMColor() {
        return "<color:" + getColor().asHexString() + ">";
    }
    
    /**
     * Get a message builder
     *
     * @return The MessageHandler
     */
    public abstract MessageHandler Builder();
    
    private @NotNull Component getMMLang(String key) {
        return MiniMessage.miniMessage().deserialize(getMMColor() + getLangString(key));
    }
    
    private @NotNull Component getMMLang(String key, TagResolver resolver) {
        return MiniMessage.miniMessage().deserialize(getMMColor() + getLangString(key), resolver);
    }
    
    public String getLangString(String key) {
        return langConfigHandler.getConfig().getValue(key).getString();
    }
    
    /**
     * Get and adds a message from the lang file
     *
     * @param key The key of the message
     * @return The MessageHandler
     */
    public MessageHandler getLang(String key) {
        message.add(getMMLang(key));
        return this;
    }
    
    /**
     * Get and adds a message from the lang file and replaces a placeholder
     *
     * @param key        The key of the message
     * @param placeholder The placeholder to replace
     * @param value       The value to replace the placeholder with
     * @return The MessageHandler
     */
    public MessageHandler getLang(String key, String placeholder, String value) {
        TagResolver.Builder builder = TagResolver.builder();
        if (placeholder == null) {
            placeholder = "";
        }
        builder.resolver(Placeholder.parsed(placeholder, value));
        message.add(getMMLang(key, builder.build()));
        return this;
    }
    
    /**
     * Get a message from the lang file with placeholders
     *
     * @param key          The key of the message
     * @param placeholders The placeholders to replace
     *                     The key is the placeholder to replace
     *                     The value is the value to replace the placeholder with
     *                     If the key or value is null or empty, it will be ignored.
     * @return The MessageHandler
     */
    public MessageHandler getLang(String key, @NotNull Map<String, String> placeholders) {
        TagResolver.Builder builder = TagResolver.builder();
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (entry.getKey().isEmpty() || entry.getValue().isEmpty()) {
                continue;
            }
            builder.resolver(Placeholder.parsed(entry.getKey(), entry.getValue()));
        }
        message.add(getMMLang(key, builder.build()));
        return this;
    }
    
    /**
     * Sets to not add the prefix to the message
     * @return The MessageHandler
     */
    public MessageHandler notAddPrefix() {
        addPrefix = false;
        return this;
    }
    
    /**
     * Adds a plain text message
     *
     * @param text The text to add
     * @return The MessageHandler
     */
    public MessageHandler text(String text) {
        message.add(Component.text(text, getColor()));
        return this;
    }
    
    public MessageHandler miniMessage(String text) {
        message.add(MiniMessage.miniMessage().deserialize(getMMColor() + text));
        return this;
    }
    
    /**
     * Adds a player to send the message to
     *
     * @param player The player to send to
     * @return The MessageHandler
     */
    public MessageHandler to(Player player) {
        recipients.add(player);
        return this;
    }
    
    /**
     * Adds a collection of players to send the message to
     *
     * @param players The players to send to
     * @return The MessageHandler
     */
    public MessageHandler to(Collection<Player> players) {
        recipients.addAll(players);
        return this;
    }
    
    /**
     * Adds all online players to send the message to
     *
     * @return The MessageHandler
     */
    public abstract MessageHandler toAll();
    
    /**
     * Adds console to the recipients
     *
     * @return The MessageHandler
     */
    public MessageHandler toConsole() {
        sendToConsole = true;
        return this;
    }
    
    /**
     * Adds console to the recipients with a log level
     *
     * @param level the log level
     * @return The MessageHandler
     */
    public MessageHandler toConsole(ConsoleLogLevel level) {
        sendToConsole = true;
        consoleLogLevel = level;
        return this;
    }
    
    /**
     * Sets to not send the message to the console
     *
     * @return The MessageHandler
     */
    public MessageHandler notToConsole() {
        sendToConsole = false;
        consoleLogLevel = ConsoleLogLevel.INFO;
        return this;
    }
    
    /**
     * Adds all players with a permission to the recipients
     *
     * @param perm the permission
     * @return The MessageHandler
     */
    public MessageHandler toPerm(Permission perm) {
        List<Player> players = permissionHandler.getPlayersWithPermission(perm);
        recipients.addAll(players);
        return this;
    }
    
    public MessageHandler addThrowable(Throwable throwable) {
        this.throwable = throwable;
        return this;
    }
    
    /**
     * Sends the message to the recipients
     */
    public void send() {
        if (sendToConsole || throwable != null) {
            sendConsole();
        }
        if (addPrefix) {
            TextComponent prefix = LegacyComponentSerializer.legacyAmpersand().deserialize(
                    configHandler.getConfig().getValue("messages", "prefix").getString());
            message.add(0, prefix);
        }
        if (configHandler.getConfig().getValue("messages", "send-in-chat").getBoolean()) {
            for (Player player : recipients) {
                sendPlayer(player);
            }
        }
    }
    
    /**
     * Gets the message
     */
    public Component getMessage() {
        return Component.join(JOIN_CONFIGURATION, message);
    }
    
    
    private void sendConsole() {
        String message = ANSI_COMPONENT_SERIALIZER.serialize(getMessage());
        switch (consoleLogLevel) {
            case INFO:
                if (throwable != null) {
                    instance.getLoggingHandler().info(message, throwable);
                } else {
                    instance.getLoggingHandler().info(message);
                }
                break;
            case WARNING:
                if (throwable != null) {
                    instance.getLoggingHandler().warn(message, throwable);
                } else {
                    instance.getLoggingHandler().warn(message);
                }
                break;
            case ERROR:
                if (throwable != null) {
                    instance.getLoggingHandler().error(message, throwable);
                } else {
                    instance.getLoggingHandler().error(message);
                }
                break;
        }
    }
    
    public void error(String key, Throwable throwable) {
        MessageHandler messageHandler = Builder();
        messageHandler.getLang(key);
        messageHandler.addThrowable(throwable);
        messageHandler.toConsole(ConsoleLogLevel.ERROR);
        messageHandler.send();
    }
    
    protected abstract void sendPlayer(Player player);
}

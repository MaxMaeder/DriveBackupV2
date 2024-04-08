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
    private boolean sendToConsole = true;
    private ConsoleLogLevel consoleLogLevel = ConsoleLogLevel.INFO;
    protected final List<Component> message = new ArrayList<>(5);
    protected final ArrayList<Player> recipients = new ArrayList<>(5);
    protected final DriveBackupInstance instance;
    private final PermissionHandler permissionHandler;
    private final ConfigHandler configHandler;
    private final LangConfigHandler langConfigHandler;
    private boolean addPrefix = true;
    
    protected MessageHandler(@NotNull DriveBackupInstance instance) {
        this.instance = instance;
        permissionHandler = instance.getPermissionHandler();
        configHandler = instance.getConfigHandler();
        langConfigHandler = instance.getLangConfigHandler();
    }
    
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
    
    public abstract MessageHandler Builder();
    
    private @NotNull Component getMMLang(String key) {
        return MiniMessage.miniMessage().deserialize(getMMColor() + langConfigHandler.getConfig().getValue(key).getString());
    }
    
    private @NotNull Component getMMLang(String key, TagResolver resolver) {
        return MiniMessage.miniMessage().deserialize(getMMColor() + langConfigHandler.getConfig().getValue(key).getString(), resolver);
    }
    
    public MessageHandler getLang(String key) {
        message.add(getMMLang(key));
        return this;
    }
    
    public MessageHandler getLang(String key, String placeholder, String value) {
        TagResolver.Builder builder = TagResolver.builder();
        builder.resolver(Placeholder.parsed(placeholder, value));
        message.add(getMMLang(key, builder.build()));
        return this;
    }
    
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
    
    public MessageHandler notAddPrefix() {
        addPrefix = false;
        return this;
    }
    
    public MessageHandler text(String text) {
        message.add(Component.text(text, getColor()));
        return this;
    }
    
    public MessageHandler to(Player player) {
        recipients.add(player);
        return this;
    }
    
    public MessageHandler to(Collection<Player> players) {
        recipients.addAll(players);
        return this;
    }
    
    public abstract MessageHandler toAll();
    
    public MessageHandler toConsole() {
        sendToConsole = true;
        return this;
    }
    
    public MessageHandler toConsole(ConsoleLogLevel level) {
        sendToConsole = true;
        consoleLogLevel = level;
        return this;
    }
    
    public MessageHandler notToConsole() {
        sendToConsole = false;
        consoleLogLevel = ConsoleLogLevel.INFO;
        return this;
    }
    
    public MessageHandler toPerm(Permission perm) {
        List<Player> players = permissionHandler.getPlayersWithPermission(perm);
        recipients.addAll(players);
        return this;
    }
    
    public void send() {
        if (addPrefix) {
            TextComponent prefix = LegacyComponentSerializer.legacyAmpersand().deserialize(
                    configHandler.getConfig().getValue("advanced", "prefix").getString());
            message.add(0, prefix);
        }
        if (sendToConsole) {
            sendConsole();
        }
        if (configHandler.getConfig().getValue("messages", "send-in-chat").getBoolean()) {
            for (Player player : recipients) {
                sendPlayer(player);
            }
        }
    }
    
    public Component getMessage() {
        return Component.join(JOIN_CONFIGURATION, message);
    }
    
    private void sendConsole() {
        String message = ANSI_COMPONENT_SERIALIZER.serialize(getMessage());
        switch (consoleLogLevel) {
            case INFO:
                instance.getLoggingHandler().info(message);
                break;
            case WARNING:
                instance.getLoggingHandler().warn(message);
                break;
            case ERROR:
                instance.getLoggingHandler().error(message);
                break;
        }
    }
    
    public abstract void sendPlayer(Player player);
}

package ratismal.drivebackupOld.config.configSections;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@Deprecated
public class Messages {
    public final boolean sendInChat;
    public final String prefix;
    public final String defaultColor;

    @Contract (pure = true)
    public Messages(
        boolean sendInChat,
        String prefix, 
        String defaultColor
        ) {
        
        this.sendInChat = sendInChat;
        this.prefix = prefix;
        this.defaultColor = defaultColor;
    }

    @NotNull
    public static Messages parse(@NotNull FileConfiguration config) {
        boolean sendInChat = config.getBoolean("messages.send-in-chat");
        String prefix = config.getString("messages.prefix");
        String defaultColor = config.getString("messages.default-color");
        return new Messages(
            sendInChat,
            prefix,
            defaultColor
        );
    }
}

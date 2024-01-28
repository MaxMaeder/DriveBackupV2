package ratismal.drivebackup.config.configSections;

import org.bukkit.configuration.file.FileConfiguration;

import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.util.Logger;

public class Messages {
    public final boolean sendInChat;
    public final String prefix;
    public final String defaultColor;

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
    public static Messages parse(@NotNull FileConfiguration config, Logger logger) {
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

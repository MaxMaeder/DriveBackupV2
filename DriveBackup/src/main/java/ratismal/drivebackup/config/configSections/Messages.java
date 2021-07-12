package ratismal.drivebackup.config.configSections;

import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.ConfigParser.Logger;

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

    public static Messages parse(FileConfiguration config, Logger logger) {
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
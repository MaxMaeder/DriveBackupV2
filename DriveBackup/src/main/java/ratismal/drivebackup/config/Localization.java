package ratismal.drivebackup.config;

import org.bukkit.configuration.file.FileConfiguration;

public class Localization {
    private static FileConfiguration intlFile;

    public Localization(FileConfiguration fileConfiguration) {
        intlFile = fileConfiguration;
    }

    public void reload(FileConfiguration fileConfiguration) {
        intlFile = fileConfiguration;
    }

    public static String intl(String key) {
        return intlFile.getString(key);
    }
}

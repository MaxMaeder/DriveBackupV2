package ratismal.drivebackup.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Contract;

@Deprecated
public final class Localization {
    private static FileConfiguration intlFile;

    @Contract (pure = true)
    private Localization() {
    }

    public static void set(FileConfiguration fileConfiguration) {
        intlFile = fileConfiguration;
    }

    public static String intl(String key) {
        if (intlFile == null) {
            return "";
        }
        return intlFile.getString(key);
    }
}

package ratismal.drivebackup.config;

import org.bukkit.configuration.ConfigurationSection;
import ratismal.drivebackup.DriveBackup;
import org.bukkit.configuration.file.FileConfiguration;
import ratismal.drivebackup.util.MessageUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Config {

    DriveBackup plugin;
    FileConfiguration pluginconfig;

    /**
     * General
     */
    private static long backupDelay;

    /**
     * Metrics
     */

    private static boolean metrics;

    /**
     * Backups
     */

    private static String dir;
    private static boolean backup;
    private static HashMap<String, HashMap<String, String>> backupList;

    /**
     * Uploading
     */

    private static String destination;

    /**
     * Google Drive
     */

    private static boolean googleEnabled;

    /**
     * OneDrive
     */

    private static boolean onedriveEnabled;
    private static String onedriveUsername;
    private static String onedrivePassword;

    /**
     * Messages
     */

    private static String noPerms;

    /**
     * config constructor
     *
     * @param driveBackup  - DriveBackup class
     * @param pluginconfig - Plugin config
     */
    public Config(DriveBackup driveBackup, FileConfiguration pluginconfig) {
        this.plugin = driveBackup;
        this.pluginconfig = pluginconfig;
    }

    public void reload() {
        this.metrics = pluginconfig.getBoolean("metrics");
        this.destination = pluginconfig.getString("destination");

        this.dir = pluginconfig.getString("dir");
        this.noPerms = pluginconfig.getString("no-perm");

        this.backup = pluginconfig.getBoolean("backup");

        this.googleEnabled = pluginconfig.getBoolean("google.enabled");

        this.onedriveEnabled = pluginconfig.getBoolean("onedrive.enabled");
        this.onedrivePassword = pluginconfig.getString("onedrive.password");
        this.onedriveUsername = pluginconfig.getString("onedrive.username");

        this.backupDelay = pluginconfig.getLong("delay") * 60 * 20;
        //MessageUtil.sendConsoleMessage("Scheduling backups for every " + backupDelay + " ticks.");

        HashMap<String, HashMap<String, String>> temp = new HashMap<String, HashMap<String, String>>();
        ConfigurationSection groupSection = pluginconfig.getConfigurationSection("backup-list");
        if (groupSection != null) {
            for (String name : groupSection.getKeys(false)) {
                HashMap<String, String> temp2 = new HashMap<String, String>();
                ConfigurationSection subSection = groupSection.getConfigurationSection(name);
                for (String name2 : subSection.getKeys(false)) {
                    String value = subSection.getString(name2);
                    temp2.put(name2, value);
                }
                temp.put(name, temp2);
            }
        }
        this.backupList = (HashMap<String, HashMap<String, String>>) temp.clone();
        /*
        for (Map.Entry<String, HashMap<String, String>> set : backupList.entrySet()) {
            MessageUtil.sendConsoleMessage("Backup: " + set.getKey() + " Format: " + set.getValue().get("format") +
                    " Create: " + set.getValue().get("create"));
        }
        */
    }


    /**
     * Returns
     */

    public static boolean isMetrics() {
        return metrics;
    }

    public static String getDir() {
        return dir;
    }

    public static String getNoPerms() {
        return noPerms;
    }

    public static String getDestination() {
        return destination;
    }

    public static boolean isBackup() {
        return backup;
    }

    public static boolean isGoogleEnabled() {
        return googleEnabled;
    }

    public static boolean isOnedriveEnabled() {
        return onedriveEnabled;
    }

    public static String getOnedriveUsername() {
        return onedriveUsername;
    }

    public static String getOnedrivePassword() {
        return onedrivePassword;
    }

    public static HashMap<String, HashMap<String, String>> getBackupList() {
        return backupList;
    }

    public static long getBackupDelay() {
        return backupDelay;
    }
}


package ratismal.drivebackup.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;

public class Config {

    private FileConfiguration pluginconfig;

    /**
     * General
     */
    private static long backupDelay;
    private static int keepCount;
    private static boolean updateCheck;
    private static boolean keepLocal;
    private static boolean debug;

    /**
     * Metrics
     */
    private static boolean metrics;

    /**
     * Backups
     */
    private static String dir;
    private static HashMap<String, HashMap<String, Object>> backupList;

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

    /**
     * FTP
     */
    private static boolean ftpEnabled;
    private static String ftpHost;
    private static int ftpPort;
    private static boolean ftpFTPS;
    private static String ftpUser;
    private static String ftpPass;
    private static String ftpDir;

    /**
     * Messages
     */

    private static String noPerms;
    private static String backupStart;
    private static String backupDone;
    private static String backupNext;


    /**
     * config constructor
     *
     * @param pluginconfig - Plugin config
     */
    public Config(FileConfiguration pluginconfig) {
        this.pluginconfig = pluginconfig;
    }

    public void reload(FileConfiguration pluginconfig) {
        this.pluginconfig = pluginconfig;
        reload();
    }

    @SuppressWarnings("unchecked")
    public void reload() {
        metrics = pluginconfig.getBoolean("metrics");

        destination = pluginconfig.getString("destination");

        dir = pluginconfig.getString("dir");
        noPerms = pluginconfig.getString("messages.no-perm");
        backupStart = pluginconfig.getString("messages.backup-start");
        backupDone = pluginconfig.getString("messages.backup-complete");
        backupNext = pluginconfig.getString("messages.next-backup");

        googleEnabled = pluginconfig.getBoolean("googledrive.enabled");

        onedriveEnabled = pluginconfig.getBoolean("onedrive.enabled");

        ftpEnabled = pluginconfig.getBoolean("ftp.enabled");
        ftpHost = pluginconfig.getString("ftp.hostname");
        ftpPort = pluginconfig.getInt("ftp.port");
        ftpFTPS = pluginconfig.getBoolean("ftp.FTPS");

        ftpUser = pluginconfig.getString("ftp.username");
        ftpPass = pluginconfig.getString("ftp.password");
        ftpDir = pluginconfig.getString("ftp.working-dir");

        backupDelay = pluginconfig.getLong("delay") * 60 * 20;
        keepCount = pluginconfig.getInt("keep-count");
        updateCheck = pluginconfig.getBoolean("update-check");
        keepLocal = pluginconfig.getBoolean("keep-local-backup-after-upload");
        debug = !pluginconfig.getBoolean("suppress-errors");

        //MessageUtil.sendConsoleMessage("Scheduling backups for every " + backupDelay + " ticks.");

        HashMap<String, HashMap<String, Object>> temp = new HashMap<>();
        ConfigurationSection groupSection = pluginconfig.getConfigurationSection("backup-list");
        //groupSection.getKeys(false)
        if (groupSection != null) {
            for (String name : groupSection.getKeys(false)) {
                HashMap<String, Object> temp2 = new HashMap<>();
                ConfigurationSection subSection = groupSection.getConfigurationSection(name);
                for (String name2 : subSection.getKeys(false)) {
                    Object value = subSection.get(name2);
                    temp2.put(name2, value);
                }
                temp.put(name, temp2);
            }
        }
        backupList = (HashMap<String, HashMap<String, Object>>) temp.clone();
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

    public static boolean isGoogleEnabled() {
        return googleEnabled;
    }

    public static boolean isOnedriveEnabled() {
        return onedriveEnabled;
    }

    public static boolean isFtpEnabled() {
        return ftpEnabled;
    }

    public static String getFtpHost() {
        return ftpHost;
    }

    public static int getFtpPort() {
        return ftpPort;
    }

    public static boolean isFtpFTPS() {
        return ftpFTPS;
    }

    public static String getFtpPass() {
        return ftpPass;
    }

    public static String getFtpUser() {
        return ftpUser;
    }

    public static String getFtpDir() {
        return ftpDir;
    }

    public static HashMap<String, HashMap<String, Object>> getBackupList() {
        return backupList;
    }

    public static long getBackupDelay() {
        return backupDelay;
    }

    public static int getKeepCount() {
        return keepCount;
    }

    public static boolean isUpdateCheck() {
        return updateCheck;
    }

    public static boolean keepLocalBackup(){
        return keepLocal;
    }

    public static String getBackupDone() {
        return backupDone;
    }

    public static String getBackupNext() {
        return backupNext;
    }

    public static String getBackupStart() {
        return backupStart;
    }

    public static boolean isDebug() {
        return debug;
    }
}


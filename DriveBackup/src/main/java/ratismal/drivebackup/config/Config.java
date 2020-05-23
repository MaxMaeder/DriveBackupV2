package ratismal.drivebackup.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.util.MessageUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.avaje.ebeaninternal.server.cluster.mcast.Message;

public class Config {

    private FileConfiguration pluginconfig;

    /**
     * General
     */
    private static long backupDelay;
    private static int backupThreadPriority;
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
    private static ArrayList<HashMap<String, Object>> backupList;

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
    private static boolean ftpSftp;
    private static boolean ftpFtps;
    private static String sftpPublicKey;
    private static String sftpPass;
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
        ftpSftp = pluginconfig.getBoolean("ftp.sftp");
        
        // Checks both FTPS keys for compatiablilty with older plugin versions
        if (pluginconfig.isSet("ftp.ftps")) {
            ftpFtps = pluginconfig.getBoolean("ftp.ftps");
        } else {
            ftpFtps = pluginconfig.getBoolean("ftp.FTPS");
        }
        

        ftpUser = pluginconfig.getString("ftp.username");
        ftpPass = pluginconfig.getString("ftp.password");
        sftpPublicKey = pluginconfig.getString("ftp.sftpPublicKey");
        sftpPass = pluginconfig.getString("ftp.sftpPassphrase");
        ftpDir = pluginconfig.getString("ftp.working-dir");

        backupDelay = pluginconfig.getLong("delay") * 60 * 20;
        backupThreadPriority = pluginconfig.getInt("backupThreadPriority");
        keepCount = pluginconfig.getInt("keep-count");
        updateCheck = pluginconfig.getBoolean("update-check");
        keepLocal = pluginconfig.getBoolean("keep-local-backup-after-upload");
        debug = !pluginconfig.getBoolean("suppress-errors");

        //MessageUtil.sendConsoleMessage("Scheduling backups for every " + backupDelay + " ticks.");

        if (pluginconfig.isList("backup-list")) {

            List<Map<?, ?>> rawBackupList = pluginconfig.getMapList("backup-list");
            ArrayList<HashMap<String, Object>> parsedBackupList = new ArrayList<>();
            for (Map<?, ?> rawBackup: rawBackupList) {

                HashMap<String, Object> parsedBackup = new HashMap<>();
                for (Entry<?, ?> rawBackupProperty : rawBackup.entrySet()) {

                    parsedBackup.put((String) rawBackupProperty.getKey(), rawBackupProperty.getValue());
                }

                parsedBackupList.add(parsedBackup);
            }

            backupList = (ArrayList<HashMap<String, Object>>) parsedBackupList.clone();
        } else { // For backwards compatibility with <1.0.2

            ConfigurationSection rawBackupList = pluginconfig.getConfigurationSection("backup-list");
            ArrayList<HashMap<String, Object>> parsedBackupList = new ArrayList<>();
            if (rawBackupList != null) {
                for (String rawBackupName : rawBackupList.getKeys(false)) {
                    
                    ConfigurationSection subSection = rawBackupList.getConfigurationSection(rawBackupName);
                    HashMap<String, Object> parsedBackup = new HashMap<>();
                    for (String rawBackupPropertyName : subSection.getKeys(false)) {

                        parsedBackup.put(rawBackupPropertyName, subSection.get(rawBackupPropertyName));
                    }

                    parsedBackup.put("path", rawBackupName);

                    parsedBackupList.add(parsedBackup);
                }
            }

            backupList = (ArrayList<HashMap<String, Object>>) parsedBackupList.clone();
        }
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

    public static boolean isFtpSftp() {
        return ftpSftp;
    }

    public static boolean isFtpFtps() {
        return ftpFtps;
    }

    public static String getFtpPass() {
        return ftpPass;
    }

    public static String getFtpUser() {
        return ftpUser;
    }

    public static String getSftpPublicKey() {
        return sftpPublicKey;
    }

    public static String getSftpPass() {
        return sftpPass;
    }

    public static String getFtpDir() {
        return ftpDir;
    }

    public static ArrayList<HashMap<String, Object>> getBackupList() {
        return backupList;
    }

    public static long getBackupDelay() {
        return backupDelay;
    }

    public static int getBackupThreadPriority() {
        return backupThreadPriority;
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


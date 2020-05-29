package ratismal.drivebackup.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Config {

    private FileConfiguration pluginconfig;

    /**
     * General
     */
    private static long backupDelay;
    private static int backupThreadPriority;
    private static int keepCount;
    private static int localKeepCount;
    private static boolean backupsRequirePlayers;
    private static boolean updateCheck;
    private static boolean debug;
    private static String dateLanguage;

    /**
     * Schedule
     */
    private static boolean scheduleBackups;
    private static ZoneOffset backupScheduleTimezone;
    private static ArrayList<HashMap<String, Object>> backupScheduleList;

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
    private static String ftpFileSeperator;

    /**
     * Messages
     */

    private static String noPerms;
    private static String backupStart;
    private static String backupDone;
    private static String backupNext;
    private static String backupNextScheduled;
    private static String backupNextScheduledFormat;
    private static boolean sendMessagesInChat;
    private static boolean prefixChatMessages;

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
        destination = pluginconfig.getString("destination");

        dir = pluginconfig.getString("dir");
        sendMessagesInChat = pluginconfig.getBoolean("messages.send-in-chat");
        noPerms = pluginconfig.getString("messages.no-perm");
        backupStart = pluginconfig.getString("messages.backup-start");
        backupDone = pluginconfig.getString("messages.backup-complete");
        backupNext = pluginconfig.getString("messages.next-backup");
        backupNextScheduled = pluginconfig.getString("messages.next-schedule-backup");
        backupNextScheduledFormat = pluginconfig.getString("messages.next-schedule-backup-format");

        scheduleBackups = pluginconfig.getBoolean("scheduled-backups");
        backupScheduleTimezone = ZoneOffset.of(pluginconfig.getString("schedule-timezone"));

        List<Map<?, ?>> rawBackupScheduleList = pluginconfig.getMapList("backup-schedule-list");
        ArrayList<HashMap<String, Object>> parsedBackupScheduleList = new ArrayList<>();
        for (Map<?, ?> rawBackupSchedule: rawBackupScheduleList) {

            HashMap<String, Object> parsedBackupSchedule = new HashMap<>();
            for (Entry<?, ?> rawBackupScheduleProperty : rawBackupSchedule.entrySet()) {

                parsedBackupSchedule.put((String) rawBackupScheduleProperty.getKey(), rawBackupScheduleProperty.getValue());
            }

            parsedBackupScheduleList.add(parsedBackupSchedule);
        }
        backupScheduleList = (ArrayList<HashMap<String, Object>>) parsedBackupScheduleList.clone();

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
        sftpPublicKey = pluginconfig.getString("ftp.sftp-public-key");
        sftpPass = pluginconfig.getString("ftp.sftp-passphrase");
        ftpDir = pluginconfig.getString("ftp.working-dir");

        backupDelay = pluginconfig.getLong("delay") * 60 * 20;
        backupThreadPriority = pluginconfig.getInt("backup-thread-priority");
        keepCount = pluginconfig.getInt("keep-count");
        localKeepCount = pluginconfig.getInt("local-keep-count");
        backupsRequirePlayers = pluginconfig.getBoolean("backups-require-players");

        // Checks both metrics, update check, and suppress errors keys for compatiablilty with older plugin versions
        if (pluginconfig.isSet("advanced.metrics")) {
            metrics = pluginconfig.getBoolean("advanced.metrics");
        } else {
            metrics = pluginconfig.getBoolean("metrics");
        }
        if (pluginconfig.isSet("advanced.update-check")) {
            updateCheck = pluginconfig.getBoolean("advanced.update-check");
        } else {
            updateCheck = pluginconfig.getBoolean("update-check");
        }
        if (pluginconfig.isSet("advanced.suppress-errors")) {
            debug = !pluginconfig.getBoolean("advanced.suppress-errors");
        } else {
            debug = !pluginconfig.getBoolean("suppress-errors");
        }

        ftpFileSeperator = pluginconfig.getString("advanced.ftp-file-seperator");
        dateLanguage = pluginconfig.getString("advanced.date-language");
        prefixChatMessages = pluginconfig.getBoolean("advanced.prefix-chat-messages");


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
                    
                    ConfigurationSection rawBackup = rawBackupList.getConfigurationSection(rawBackupName);
                    HashMap<String, Object> parsedBackup = new HashMap<>();
                    for (String rawBackupPropertyName : rawBackup.getKeys(false)) {

                        parsedBackup.put(rawBackupPropertyName, rawBackup.get(rawBackupPropertyName));
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

    public static String getFtpFileSeperator() {
        return ftpFileSeperator;
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

    public static boolean isBackupsScheduled() {
        return scheduleBackups;
    }

    public static ZoneOffset getBackupScheduleTimezone() {
        return backupScheduleTimezone;
    }

    public static ArrayList<HashMap<String, Object>> getBackupScheduleList() {
        return backupScheduleList;
    }

    public static int getKeepCount() {
        return keepCount;
    }

    public static int getLocalKeepCount() {
        return localKeepCount;
    }

    public static boolean isBackupsRequirePlayers() {
        return backupsRequirePlayers;
    }

    public static boolean isUpdateCheck() {
        return updateCheck;
    }

    public static boolean isSendMessagesInChat() {
        return sendMessagesInChat;
    }

    public static boolean isPrefixChatMessages() {
        return prefixChatMessages;
    }

    public static String getDateLanguage() {
        return dateLanguage;
    }

    public static String getBackupDone() {
        return backupDone;
    }

    public static String getBackupNext() {
        return backupNext;
    }

    public static String getBackupNextScheduled() {
        return backupNextScheduled;
    }

    public static String getBackupNextScheduledFormat() {
        return backupNextScheduledFormat;
    }

    public static String getBackupStart() {
        return backupStart;
    }

    public static boolean isDebug() {
        return debug;
    }
}


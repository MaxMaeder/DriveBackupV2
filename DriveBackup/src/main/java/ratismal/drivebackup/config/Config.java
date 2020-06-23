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
    private FileConfiguration config;

    private static long backupDelay;
    private static int backupThreadPriority;
    private static int keepCount;
    private static int localKeepCount;
    private static boolean backupsRequirePlayers;

    private static boolean scheduleBackups;
    private static ZoneOffset backupScheduleTimezone;
    private static ArrayList<HashMap<String, Object>> backupScheduleList;

    private static ArrayList<HashMap<String, Object>> backupList;

    private static ArrayList<HashMap<String, Object>> externalBackupList;

    private static String dir;

    private static String destination;

    private static boolean googleEnabled;
    private static String googleTeamDriveName;
    private static boolean onedriveEnabled;
    private static boolean ftpEnabled;
    private static String ftpHost;
    private static int ftpPort;
    private static boolean ftpSftp;
    private static boolean ftpFtps;
    private static String ftpUser;
    private static String ftpPass;
    private static String ftpDir;
    private static String sftpPublicKey;
    private static String sftpPass;
    
    private static boolean sendMessagesInChat;
    private static String noPerms;
    private static String backupStart;
    private static String backupDone;
    private static String backupNext;
    private static String backupNextScheduled;
    private static String backupNextScheduledFormat;
    
    private static boolean metrics;
    private static boolean updateCheck;
    private static boolean debug;
    private static boolean prefixChatMessages;
    private static String ftpFileSeperator;
    private static String dateLanguage;

    /**
     * Creates an instance of the {@code Config} object
     * @param config A reference to the plugin's {@code config.yml}
     */
    public Config(FileConfiguration config) {
        this.config = config;
    }

    /**
     * Reloads the plugin's {@code config.yml}
     * @param config A reference to the plugin's {@code config.yml}
     */
    public void reload(FileConfiguration config) {
        this.config = config;
        reload();
    }

    /**
     * Reloads the plugin's {@code config.yml}
     */
    @SuppressWarnings("unchecked")
    public void reload() {
        backupDelay = config.getLong("delay") * 60 * 20;
        backupThreadPriority = config.getInt("backup-thread-priority");
        keepCount = config.getInt("keep-count");
        localKeepCount = config.getInt("local-keep-count");
        backupsRequirePlayers = config.getBoolean("backups-require-players");

        scheduleBackups = config.getBoolean("scheduled-backups");
        backupScheduleTimezone = ZoneOffset.of(config.getString("schedule-timezone"));
        List<Map<?, ?>> rawBackupScheduleList = config.getMapList("backup-schedule-list");
        ArrayList<HashMap<String, Object>> parsedBackupScheduleList = new ArrayList<>();
        for (Map<?, ?> rawBackupSchedule: rawBackupScheduleList) {

            HashMap<String, Object> parsedBackupSchedule = new HashMap<>();
            for (Entry<?, ?> rawBackupScheduleProperty : rawBackupSchedule.entrySet()) {

                parsedBackupSchedule.put((String) rawBackupScheduleProperty.getKey(), rawBackupScheduleProperty.getValue());
            }

            parsedBackupScheduleList.add(parsedBackupSchedule);
        }
        backupScheduleList = (ArrayList<HashMap<String, Object>>) parsedBackupScheduleList.clone();

        if (config.isList("backup-list")) {

            List<Map<?, ?>> rawBackupList = config.getMapList("backup-list");
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

            ConfigurationSection rawBackupList = config.getConfigurationSection("backup-list");
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

        List<Map<?, ?>> rawExternalBackupList = config.getMapList("external-backup-list");
        ArrayList<HashMap<String, Object>> parsedExternalBackupList = new ArrayList<>();
        for (Map<?, ?> rawExternalBackup: rawExternalBackupList) {

            HashMap<String, Object> parsedExternalBackup = new HashMap<>();
            for (Entry<?, ?> rawExternalBackupProperty : rawExternalBackup.entrySet()) {

                parsedExternalBackup.put((String) rawExternalBackupProperty.getKey(), rawExternalBackupProperty.getValue());
            }

            parsedExternalBackupList.add(parsedExternalBackup);
        }
        externalBackupList = (ArrayList<HashMap<String, Object>>) parsedExternalBackupList.clone();

        dir = config.getString("dir");

        destination = config.getString("destination");

        googleEnabled = config.getBoolean("googledrive.enabled");
        googleTeamDriveName = config.getString("googledrive.team-drive-name");
        onedriveEnabled = config.getBoolean("onedrive.enabled");
        ftpEnabled = config.getBoolean("ftp.enabled");
        ftpHost = config.getString("ftp.hostname");
        ftpPort = config.getInt("ftp.port");
        ftpSftp = config.getBoolean("ftp.sftp");
        ftpFtps = getBooleanWithFallback("ftp.ftps", "ftp.FTPS");
        ftpUser = config.getString("ftp.username");
        ftpPass = config.getString("ftp.password");
        sftpPublicKey = config.getString("ftp.sftp-public-key");
        sftpPass = config.getString("ftp.sftp-passphrase");
        ftpDir = config.getString("ftp.working-dir");

        sendMessagesInChat = config.getBoolean("messages.send-in-chat");
        noPerms = config.getString("messages.no-perm");
        backupStart = config.getString("messages.backup-start");
        backupDone = config.getString("messages.backup-complete");
        backupNext = config.getString("messages.next-backup");
        backupNextScheduled = config.getString("messages.next-schedule-backup");
        backupNextScheduledFormat = config.getString("messages.next-schedule-backup-format");

        metrics = getBooleanWithFallback("advanced.metrics", "metrics");
        updateCheck = getBooleanWithFallback("advanced.update-check", "update-check");
        debug = getBooleanWithFallback("advanced.suppress-errors", "suppress-errors");
        prefixChatMessages = config.getBoolean("advanced.prefix-chat-messages");
        ftpFileSeperator = config.getString("advanced.ftp-file-seperator");
        dateLanguage = config.getString("advanced.date-language");
    } 

    /**
     * Gets the value at the specifed path, or gets the value at the specifed fallback path if there isn't a value at the specifed path
     * @param path the path
     * @param fallbackPath the fallback path
     * @return the value
     */
    private boolean getBooleanWithFallback(String path, String fallbackPath) {
        if (config.isSet(path)) {
           return config.getBoolean(path);
        } else {
            return config.getBoolean(fallbackPath);
        }
    }

    /**
     * Gets the interval at which to run interval based backups
     * @return the interval
     */
    public static long getBackupDelay() {
        return backupDelay;
    }

    /**
     * Gets the priority of the backup thread, relative to the minimum
     * @return the priority of the thread
     */
    public static int getBackupThreadPriority() {
        return backupThreadPriority;
    }

    /**
     * Gets the number of backups to keep remotely
     * @return the number of backups
     */
    public static int getKeepCount() {
        return keepCount;
    }

    /**
     * Gets the number of backups to keep locally
     * @return the number of backups
     */
    public static int getLocalKeepCount() {
        return localKeepCount;
    }

    /**
     * Gets whether backups shouldn't run if no new player activity has occurred
     * @return whether to run if no new activity has occurred
     */
    public static boolean isBackupsRequirePlayers() {
        return backupsRequirePlayers;
    }

    /**
     * Gets whether schedule-based backups are enabled
     * @return whether they are enabled
     */
    public static boolean isBackupsScheduled() {
        return scheduleBackups;
    }

    /**
     * Gets the timezone of the schedule-based backup list
     * @return the timezone
     */
    public static ZoneOffset getBackupScheduleTimezone() {
        return backupScheduleTimezone;
    }

    /**
     * Gets the schedule-based backup list
     * @return the list
     */
    public static ArrayList<HashMap<String, Object>> getBackupScheduleList() {
        return backupScheduleList;
    }

    /**
     * Gets the list of items to backup
     * @return the list
     */
    public static ArrayList<HashMap<String, Object>> getBackupList() {
        return backupList;
    }

    public static ArrayList<HashMap<String, Object>> getExternalBackupList() {
        return externalBackupList;
    }

    /**
     * Gets the path to the folder to store backups in locally
     * @return the path
     */
    public static String getDir() {
        return dir;
    }

    /**
     * Gets the path to the folder to store backups in remotly
     * @return the path
     */
    public static String getDestination() {
        return destination;
    }

    /**
     * Gets whether a Google Drive is enabled as a backup method
     * @return whether Google Drive is enabled
     */
    public static boolean isGoogleEnabled() {
        return googleEnabled;
    }

    /**
     * Gets the name of the Google Drive team drive to store files in
     * @return the name of the team drive
     */
    public static String getGoogleDriveTeamDriveName() {
        return googleTeamDriveName;
    }

    /**
     * Gets whether a OneDrive is enabled as a backup method
     * @return whether OneDrive is enabled
     */
    public static boolean isOnedriveEnabled() {
        return onedriveEnabled;
    }

    /**
     * Gets whether a FTP is enabled as a backup method
     * @return whether FTP is enabled
     */
    public static boolean isFtpEnabled() {
        return ftpEnabled;
    }

    /**
     * Gets the hostname of the (S)FTP server
     * @return the hostname
     */
    public static String getFtpHost() {
        return ftpHost;
    }

    /**
     * Gets the port of the (S)FTP server
     * @return the port
     */
    public static int getFtpPort() {
        return ftpPort;
    }

    /**
     * Gets whether to use SFTP when connecting to the FTP server
     * @return whether to use SFTP
     */
    public static boolean isFtpSftp() {
        return ftpSftp;
    }

    /**
     * Gets whether to use FTPS when connecting to the FTP server
     * @return whether to use FTPS
     */
    public static boolean isFtpFtps() {
        return ftpFtps;
    }

    /**
     * Gets the username to use when connecting to the (S)FTP server
     * @return the username
     */
    public static String getFtpUser() {
        return ftpUser;
    }

    /**
     * Gets the password to use when connecting to the (S)FTP server
     * @return the password
     */
    public static String getFtpPass() {
        return ftpPass;
    }

    /**
     * Gets the path to the public key to use when connecting to the SFTP server, relative to the {@code DriveBackupV2} folder
     * @return the passphrase
     */
    public static String getSftpPublicKey() {
        return sftpPublicKey;
    }

    /**
     * Gets the passphrase to use when connecting to the SFTP server
     * @return the passphrase
     */
    public static String getSftpPass() {
        return sftpPass;
    }

    /**
     * Gets the working directory to use when uploading to a (S)FTP server
     * @return the working directory
     */
    public static String getFtpDir() {
        return ftpDir;
    }

    /**
     * Gets whether to send public facing messages in chat, or just the server console
     * @return whether to send messages in chat
     */
    public static boolean isSendMessagesInChat() {
        return sendMessagesInChat;
    } 

    /**
     * Gets the message to send a player to notify them that they don't have permissions to run a command
     * @return the message
     */
    public static String getNoPerms() {
        return noPerms;
    }

    /**
     * Gets the message to send to players to notify them of a backup starting
     * @return the message
     */
    public static String getBackupStart() {
        return backupStart;
    }

    /**
     * Gets the message to send to players to notify them of a backup finishing
     * @return the message
     */
    public static String getBackupDone() {
        return backupDone;
    }

    /**
     * Gets the message to send to players to notify them of the next interval-based update
     * @return the message
     */
    public static String getBackupNext() {
        return backupNext;
    }

    /**
     * Gets the message to send to players to notify them of the next schedule-based update
     * @return the message
     */
    public static String getBackupNextScheduled() {
        return backupNextScheduled;
    }

    /**
     * Gets the format of the date/time of the next backup to display
     * @return the format, represented by date and time pattern letters
     */
    public static String getBackupNextScheduledFormat() {
        return backupNextScheduledFormat;
    }

    /**
     * Gets whether to use log anonymous metrics
     * @return whether to log them
     */
    public static boolean isMetrics() {
        return metrics;
    }

    /**
     * Gets whether the plugin should look for updates
     * @return whether to look for updates
     */
    public static boolean isUpdateCheck() {
        return updateCheck;
    }

    /**
     * Gets whether stack traces should be logged in the server console
     * @return whether they should be logged
     */
    public static boolean isDebug() {
        return debug;
    }

    /**
     * Gets whether the public facing messages should be prefixed with the plugin name
     * @return whether to prefix them
     */
    public static boolean isPrefixChatMessages() {
        return prefixChatMessages;
    }

    /**
     * Gets the file seperator to use with FTP servers
     * @return the file seperator
     */
    public static String getFtpFileSeperator() {
        return ftpFileSeperator;
    }

    /**
     * Gets the language to use with date and time pattern letters
     * @return the language in ISO 639-2 format
     */
    public static String getDateLanguage() {
        return dateLanguage;
    }
}


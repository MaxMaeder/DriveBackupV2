package ratismal.drivebackup;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import ratismal.drivebackup.Uploaders.Uploader;
import ratismal.drivebackup.Uploaders.dropbox.DropboxUploader;
import ratismal.drivebackup.Uploaders.ftp.FTPUploader;
import ratismal.drivebackup.Uploaders.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.Uploaders.onedrive.OneDriveUploader;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.config.configSections.BackupList.BackupListEntry;
import ratismal.drivebackup.config.configSections.BackupList.BackupListEntry.PathBackupLocation;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalBackupSource;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalFTPSource;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalMySQLSource;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalFTPSource.ExternalBackupListEntry;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalMySQLSource.MySQLDatabaseBackup;
import ratismal.drivebackup.handler.PlayerListener;
import ratismal.drivebackup.mysql.MySQLUploader;
import ratismal.drivebackup.plugin.Scheduler;
import ratismal.drivebackup.util.*;
import ratismal.drivebackup.util.Timer;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static ratismal.drivebackup.config.Localization.intl;

/**
 * Created by Ratismal on 2016-01-22.
 */

public class UploadThread implements Runnable {
    private CommandSender initiator;

    private static LocalDateTime nextIntervalBackupTime = null;

    /**
     * The current status of the backup thread
     */
    enum BackupStatus {
        /**
         * The backup thread isn't running
         */
        NOT_RUNNING,

        /**
         * The backup thread is compressing the files to be backed up
         */
        COMPRESSING,

        /**
         * The backup thread is uploading the files
         */
        UPLOADING
    }

    /**
     * The list of items to be backed up by the backup thread
     */
    private static List<BackupListEntry> backupList;

    /**
     * The {@code BackupStatus} of the backup thread
     */
    private static BackupStatus backupStatus = BackupStatus.NOT_RUNNING;

    /**
     * The backup currently being backed up by the 
     */
    private static int backupBackingUp = 0;

    /**
     * Creates an instance of the {@code UploadThread} object
     */
    public UploadThread() {
    }

    /**
     * Creates an instance of the {@code UploadThread} object
     * @param initiator the player who initiated the backup
     */
    public UploadThread(CommandSender initiator) {
        this.initiator = initiator;
    }

    /**
     * Starts a backup
     */
    @Override
    public void run() {
        Config config = ConfigParser.getConfig();

        if (initiator != null && backupStatus != BackupStatus.NOT_RUNNING) {
            MessageUtil.sendMessage(initiator, "A backup is already running");
            MessageUtil.sendMessage(initiator, getBackupStatus());

            return;
        }

        if (initiator == null) {
            updateNextIntervalBackupTime();
        }

        Thread.currentThread().setPriority(config.backupStorage.threadPriority);

        if (!DriveBackupApi.shouldStartBackup()) {
            return;
        }

        if (config.backupStorage.backupsRequirePlayers && !PlayerListener.isAutoBackupsActive() && initiator == null) {
            MessageUtil.sendConsoleMessage("Skipping backup due to inactivity");

            return;
        }

        boolean errorOccurred = false;

        if (
            !config.backupMethods.googleDrive.enabled && 
            !config.backupMethods.oneDrive.enabled && 
            !config.backupMethods.dropbox.enabled && !config.backupMethods.ftp.enabled && 
            config.backupStorage.localKeepCount == 0
            ) {

            MessageUtil.sendMessageToPlayersWithPermission("No backup method is enabled", "drivebackup.linkAccounts", Collections.singletonList(initiator), true);

            return;
        }

        ServerUtil.setAutoSave(false);

        MessageUtil.sendMessageToAllPlayers(intl("backup-start"));


        ArrayList<Uploader> uploaders = new ArrayList<Uploader>();

        if (config.backupMethods.googleDrive.enabled) {
            uploaders.add(new GoogleDriveUploader());
        }
        if (config.backupMethods.oneDrive.enabled) {
            uploaders.add(new OneDriveUploader());
        }
        if (config.backupMethods.dropbox.enabled) {
            uploaders.add(new DropboxUploader());
        }
        if (config.backupMethods.ftp.enabled) {
            uploaders.add(new FTPUploader());
        }

        backupList = config.backupList.list;

        List<ExternalBackupSource> externalBackupList = config.externalBackups.sources;
        for (ExternalBackupSource externalBackup : externalBackupList) {
            if (externalBackup instanceof ExternalFTPSource) {
                makeExternalFileBackup((ExternalFTPSource) externalBackup);
            } else {
                makeExternalDatabaseBackup((ExternalMySQLSource) externalBackup);
            }
        }

        backupBackingUp = 0;
        for (BackupListEntry set : backupList) {
            for(Path folder : set.location.getPaths()) {
                Boolean err = doSingleBackup(folder.toString(), set.formatter, set.create, Arrays.asList(set.blacklist), uploaders);
                if(err) { // an error occurred
                    backupStatus = BackupStatus.NOT_RUNNING;
                    errorOccurred = true;
                    ServerUtil.setAutoSave(true);
                    return;
                }
            }

            backupBackingUp++;
        }

        deleteFolder(new File("external-backups"));

        backupStatus = BackupStatus.NOT_RUNNING;
            
        if (config.backupStorage.localKeepCount != 0) {
            MessageUtil.sendMessageToPlayersWithPermission(ChatColor.GOLD + "Local " + ChatColor.DARK_AQUA + "backup complete", "drivebackup.linkAccounts", Collections.singletonList(initiator), false);
        }

        for(int i = 0; i < uploaders.size(); i++) {
            uploaders.get(i).close();
            if (uploaders.get(i).isErrorWhileUploading()) {
                MessageUtil.sendMessageToPlayersWithPermission(uploaders.get(i).getSetupInstructions(), "drivebackup.linkAccounts", Collections.singletonList(initiator));
                errorOccurred = true;
            } else {
                MessageUtil.sendMessageToPlayersWithPermission("Backup to " + ChatColor.GOLD + uploaders.get(i).getName() + ChatColor.DARK_AQUA + " complete", "drivebackup.linkAccounts", Collections.singletonList(initiator), false);
            }
        }

        if (initiator != null) {
            MessageUtil.sendMessageToAllPlayers(intl("backup-complete"));
        } else {
            MessageUtil.sendMessageToAllPlayers(intl("backup-complete") + " " + getNextAutoBackup());
        }

        if (config.backupStorage.backupsRequirePlayers && Bukkit.getOnlinePlayers().size() == 0 && PlayerListener.isAutoBackupsActive()) {
            MessageUtil.sendConsoleMessage("Disabling automatic backups due to inactivity");
            PlayerListener.setAutoBackupsActive(false);
        }

        ServerUtil.setAutoSave(true);

        if (errorOccurred) {
            DriveBackupApi.backupError();
        } else {
            DriveBackupApi.backupDone();
        }
    }

    /**
     * Backs up a single folder
     * @param type Path to the folder
     * @param formatter Save format configuration
     * @param create Create the zip file or just upload it? ("True" / "False")
     * @param blackList configured blacklist (with globs)
     * @param uploaders All servies to upload to
     * @return True if any error occurred
     */
    private Boolean doSingleBackup(String type, LocalDateTimeFormatter  formatter, boolean create, List<String> blackList, List<Uploader> uploaders) {
        MessageUtil.sendConsoleMessage("Doing backups for \"" + type + "\"");
        if (create) {
            backupStatus = BackupStatus.COMPRESSING;

            try {
                FileUtil.makeBackup(type, formatter, blackList);
            } catch (IllegalArgumentException exception) {
                MessageUtil.sendMessageToPlayersWithPermission("Failed to create a backup, path to folder to backup is absolute, expected a relative path", "drivebackup.linkAccounts", Collections.singletonList(initiator), true);
                MessageUtil.sendMessageToPlayersWithPermission("An absolute path can overwrite sensitive files, see the " + ChatColor.GOLD + "config.yml " + ChatColor.DARK_AQUA + "for more information", "drivebackup.linkAccounts", Collections.singletonList(initiator), true);

                backupStatus = BackupStatus.NOT_RUNNING;

                ServerUtil.setAutoSave(true);

                return true;
            } catch (Exception exception) {
                MessageUtil.sendConsoleException(exception);
                MessageUtil.sendMessageToPlayersWithPermission("Failed to create a backup", "drivebackup.linkAccounts", Collections.singletonList(initiator), true);

                backupStatus = BackupStatus.NOT_RUNNING;

                ServerUtil.setAutoSave(true);

                return true;
            }
        }

        try {
            backupStatus = BackupStatus.UPLOADING;

            if (FileUtil.isBaseFolder(type)) {
                type = "root";
            }

            File file = FileUtil.getNewestBackup(type, formatter);
            ratismal.drivebackup.util.Timer timer = new Timer();


            for(int i = 0; i < uploaders.size(); i++) {
                MessageUtil.sendConsoleMessage("Uploading file to " + uploaders.get(i).getName());
                timer.start();
                uploaders.get(i).uploadFile(file, type);
                timer.end();
                if(!uploaders.get(i).isErrorWhileUploading()) {
                    MessageUtil.sendConsoleMessage(timer.getUploadTimeMessage(file));
                } else {
                    MessageUtil.sendConsoleMessage("Upload failed");
                }
            }

            FileUtil.deleteFiles(type, formatter);
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
        }
        return false;
    }

    /**
     * Downloads files from a FTP server and stores them within the external-backups temporary folder, using the specified external backup settings
     * @param externalBackup the external backup settings
     */
    private void makeExternalFileBackup(ExternalFTPSource externalBackup) {
        MessageUtil.sendConsoleMessage("Downloading files from a (S)FTP server (" + getSocketAddress(externalBackup) + ") to include in backup");

        FTPUploader ftpUploader = new FTPUploader(
                externalBackup.hostname, 
                externalBackup.port, 
                externalBackup.username, 
                externalBackup.password,
                externalBackup.ftps,
                externalBackup.sftp,
                externalBackup.publicKey, 
                externalBackup.passphrase,
                "external-backups",
                ".");

        for (ExternalBackupListEntry backup : externalBackup.backupList) {
            ArrayList<BlacklistEntry> blacklist = new ArrayList<>();

            for (String blacklistGlob : backup.blacklist) {
                BlacklistEntry blacklistEntry = new BlacklistEntry(
                    blacklistGlob, 
                    FileSystems.getDefault().getPathMatcher("glob:" + blacklistGlob)
                    );
    
                blacklist.add(blacklistEntry);
            }

            String baseDirectory;
            if (externalBackup.baseDirectory.trim().isEmpty()) {
                baseDirectory = backup.path;
            } else {
                baseDirectory = externalBackup.baseDirectory + "/" + backup.path;
            }

            for (String relativeFilePath : ftpUploader.getFiles(baseDirectory)) {
                String filePath = baseDirectory + "/" + relativeFilePath;

                for (BlacklistEntry blacklistEntry : blacklist) {
                    if (blacklistEntry.getPathMatcher().matches(Paths.get(relativeFilePath))) {
                        blacklistEntry.incrementBlacklistedFiles();
    
                        continue;
                    } 
                }

                String parentFolder = new File(relativeFilePath).getParent();
                String parentFolderPath;
                if (parentFolder != null) {
                    parentFolderPath = "/" + parentFolder;
                } else {
                    parentFolderPath = "";
                }

                ftpUploader.downloadFile(filePath, getTempFolderName(externalBackup) + "/" + backup.path + parentFolderPath);
            }

            for (BlacklistEntry blacklistEntry : blacklist) {
                String globPattern = blacklistEntry.getGlobPattern();
                int blacklistedFiles = blacklistEntry.getBlacklistedFiles();
    
                if (blacklistedFiles > 0) {
                    MessageUtil.sendConsoleMessage("Didn't include " + blacklistedFiles + " file(s) in the backup from the external (S)FTP server, as they are blacklisted by \"" + globPattern + "\"");
                }
            }
        }

        ftpUploader.close();

        BackupListEntry backup = new BackupListEntry(
            new PathBackupLocation("external-backups" + File.separator + getTempFolderName(externalBackup)),
            externalBackup.format,
            true,
            new String[0]
        );
        backupList.add(backup);

        if (ftpUploader.isErrorWhileUploading()) {
            MessageUtil.sendMessageToPlayersWithPermission("Failed to include files from a (S)FTP server (" + getSocketAddress(externalBackup) + ") in the backup, please check the server credentials in the " + ChatColor.GOLD + "config.yml", "drivebackup.linkAccounts", Collections.singletonList(initiator), false);
        } else {
            MessageUtil.sendMessageToPlayersWithPermission("Files from a " + ChatColor.GOLD + "(S)FTP server (" + getSocketAddress(externalBackup) + ") " + ChatColor.DARK_AQUA + "were successfully included in the backup", "drivebackup.linkAccounts", Collections.singletonList(initiator), false);
        }
    }

    /**
     * Downloads databases from a MySQL server and stores them within the external-backups temporary folder, using the specified external backup settings
     * @param externalBackup the external backup settings
     */
    private void makeExternalDatabaseBackup(ExternalMySQLSource externalBackup) {
        MessageUtil.sendConsoleMessage("Downloading databases from a MySQL server (" + getSocketAddress(externalBackup) + ") to include in backup");

        MySQLUploader mysqlUploader = new MySQLUploader(
                externalBackup.hostname, 
                externalBackup.port, 
                externalBackup.username, 
                externalBackup.password,
                externalBackup.ssl);

        for (MySQLDatabaseBackup database : externalBackup.databaseList) {
            for (String blacklistEntry : database.blacklist) {
                MessageUtil.sendConsoleMessage("Didn't include table \"" + blacklistEntry + "\" in the backup, as it is blacklisted");
            }

            mysqlUploader.downloadDatabase(database.name, getTempFolderName(externalBackup), Arrays.asList(database.blacklist));
        }

        BackupListEntry backup = new BackupListEntry(
            new PathBackupLocation("external-backups" + File.separator + getTempFolderName(externalBackup)),
            externalBackup.format,
            true,
            new String[0]
        );
        backupList.add(backup);

        if (mysqlUploader.isErrorWhileUploading()) {
            MessageUtil.sendMessageToPlayersWithPermission("Failed to include databases from a MySQL server (" + getSocketAddress(externalBackup) + ") in the backup, please check the server credentials in the " + ChatColor.GOLD + "config.yml", "drivebackup.linkAccounts", Collections.singletonList(initiator), false);
        } else {
            MessageUtil.sendMessageToPlayersWithPermission("Databases from a " + ChatColor.GOLD + "MySQL server (" + getSocketAddress(externalBackup) + ") " + ChatColor.DARK_AQUA + "were successfully included in the backup", "drivebackup.linkAccounts", Collections.singletonList(initiator), false);
        }
    }

    /**
     * Gets the current status of the backup thread
     * @return the status of the backup thread as a {@code String}
     */
    public static String getBackupStatus() {
        Config config = ConfigParser.getConfig();
        StringBuilder backupStatusMessage = new StringBuilder();

        if (backupStatus == BackupStatus.NOT_RUNNING) {
            backupStatusMessage.append("No backups are running");

            return backupStatusMessage.toString();
        }

        switch (backupStatus) {
            case COMPRESSING: backupStatusMessage.append("Compressing ");
                break;
            case UPLOADING: backupStatusMessage.append("Uploading ");
                break;
            default:
        }

        List<BackupListEntry> backupList = config.backupList.list;
        String backupSetName = backupList.get(backupBackingUp).location.toString();

        backupStatusMessage.append("backup set \"" + backupSetName + "\", set " + (backupBackingUp + 1) + " of " + backupList.size());

        return backupStatusMessage.toString();
    }

    /**
     * Gets the date/time of the next automatic backup, if enabled
     * @return the time and/or date of the next automatic backup formatted using the messages in the {@code config.yml} 
     */
    public static String getNextAutoBackup() {
        Config config = ConfigParser.getConfig();
        String nextBackupMessage = "";

        if (config.backupScheduling.enabled) {

            ZonedDateTime nextBackupDate = null;

            ZonedDateTime now = ZonedDateTime.now(config.advanced.dateTimezone);

            int weeksCheckedForDate;
            for (weeksCheckedForDate = 0; weeksCheckedForDate < 2; weeksCheckedForDate++) {
                for (ZonedDateTime date : Scheduler.getBackupDatesList()) {

                    if (nextBackupDate == null &&

                        ((LocalTime.from(date).isAfter(LocalTime.from(now)) && // This might not work if time specified is 00:00
                        date.getDayOfWeek().compareTo(now.getDayOfWeek()) == 0) ||

                        date.getDayOfWeek().compareTo(now.getDayOfWeek()) > 0)
                    ) {
                        nextBackupDate = date;
                        continue;
                    }

                    if (nextBackupDate != null &&

                        ((LocalTime.from(date).isBefore(LocalTime.from(nextBackupDate)) && // This might not work if time specified is 00:00
                        LocalTime.from(date).isAfter(LocalTime.from(now)) &&
                        (date.getDayOfWeek().compareTo(nextBackupDate.getDayOfWeek()) == 0 ||
                        date.getDayOfWeek().compareTo(now.getDayOfWeek()) == 0)) || 

                        (date.getDayOfWeek().compareTo(nextBackupDate.getDayOfWeek()) < 0 &&
                        date.getDayOfWeek().compareTo(now.getDayOfWeek()) > 0))
                    ) {
                        nextBackupDate = date;
                    }
                }

                if (nextBackupDate != null) {
                    break;
                }

                now = now
                    .with(ChronoField.DAY_OF_WEEK, 1)
                    .with(ChronoField.CLOCK_HOUR_OF_DAY, 1)
                    .with(ChronoField.MINUTE_OF_HOUR, 0)
                    .with(ChronoField.SECOND_OF_DAY, 0);
            }

            if (weeksCheckedForDate == 1) {
                nextBackupDate = nextBackupDate
                    .with(ChronoField.YEAR, now.get(ChronoField.YEAR))
                    .with(ChronoField.ALIGNED_WEEK_OF_YEAR, now.get(ChronoField.ALIGNED_WEEK_OF_YEAR) + 1);
            } else {
                nextBackupDate = nextBackupDate
                    .with(ChronoField.YEAR, now.get(ChronoField.YEAR))
                    .with(ChronoField.ALIGNED_WEEK_OF_YEAR, now.get(ChronoField.ALIGNED_WEEK_OF_YEAR));
            }

            DateTimeFormatter backupDateFormatter = DateTimeFormatter.ofPattern(intl("next-schedule-backup-format"), config.advanced.dateLanguage);
            nextBackupMessage = intl("next-schedule-backup").replaceAll("%DATE", nextBackupDate.format(backupDateFormatter));
        } else if (config.backupStorage.delay != -1) {
            nextBackupMessage = intl("next-backup").replaceAll("%TIME", String.valueOf(LocalDateTime.now().until(nextIntervalBackupTime, ChronoUnit.MINUTES)));
        } else {
            nextBackupMessage = intl("auto-backups-disabled");
        }

        return nextBackupMessage;
    }

    /**
     * Sets the time of the next interval-based backup to the current time + the configured interval
     */
    public static void updateNextIntervalBackupTime() {
        nextIntervalBackupTime = LocalDateTime.now().plus(ConfigParser.getConfig().backupStorage.delay, ChronoUnit.MINUTES);
    }

    /**
     * Gets the socket address (ipaddress/hostname:port) of an external backup server based on the specified settings
     * @param externalBackup the external backup settings
     * @return the socket address
     */
    private static String getSocketAddress(ExternalBackupSource externalBackup) {
        return externalBackup.hostname + "-" + externalBackup.port;
    }

    /**
     * Generates the name for a folder based on the specified external backup settings to be stored within the external-backups temporary folder
     * @param externalBackup the external backup settings
     * @return the folder name
     */
    private static String getTempFolderName(ExternalBackupSource externalBackup) {
        if (externalBackup instanceof ExternalFTPSource) {
            return "ftp-" + getSocketAddress(externalBackup);
        } else {
            return "mysql-" + getSocketAddress(externalBackup);
        }
    }

    /**
     * Deletes the specified folder
     * @param folder the folder to be deleted
     * @return whether deleting the folder was successful
     */
    private static boolean deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteFolder(file);
            }
        }
        return folder.delete();
    }
}

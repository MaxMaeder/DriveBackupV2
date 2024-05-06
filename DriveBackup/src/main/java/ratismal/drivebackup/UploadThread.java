package ratismal.drivebackup;

import com.google.api.client.util.Strings;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.config.configSections.BackupList.BackupListEntry;
import ratismal.drivebackup.config.configSections.BackupList.BackupListEntry.PathBackupLocation;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalBackupSource;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalFTPSource;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalFTPSource.ExternalBackupListEntry;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalMySQLSource;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalMySQLSource.MySQLDatabaseBackup;
import ratismal.drivebackup.constants.Permission;
import ratismal.drivebackup.handler.listeners.PlayerListener;
import ratismal.drivebackup.plugin.Scheduler;
import ratismal.drivebackup.uploaders.Authenticator;
import ratismal.drivebackup.uploaders.Authenticator.AuthenticationProvider;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.uploaders.dropbox.DropboxUploader;
import ratismal.drivebackup.uploaders.ftp.FTPUploader;
import ratismal.drivebackup.uploaders.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.uploaders.mysql.MySQLUploader;
import ratismal.drivebackup.uploaders.onedrive.OneDriveUploader;
import ratismal.drivebackup.uploaders.s3.S3Uploader;
import ratismal.drivebackup.uploaders.webdav.NextcloudUploader;
import ratismal.drivebackup.uploaders.webdav.WebDAVUploader;
import ratismal.drivebackup.util.BlacklistEntry;
import ratismal.drivebackup.util.FileUtil;
import ratismal.drivebackup.util.LocalDateTimeFormatter;
import ratismal.drivebackup.util.Logger;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.ServerUtil;
import ratismal.drivebackup.util.Timer;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static ratismal.drivebackup.config.Localization.intl;

/**
 * Created by Ratismal on 2016-01-22.
 */

public class UploadThread implements Runnable {
    
    private static final String LINK_COMMAND = "/drivebackup linkaccount ";
    private CommandSender initiator;
    private final UploadLogger logger;
    private final FileUtil fileUtil;

    /**
     * The current status of the backup thread
     */
    enum BackupStatus {
        /**
         * The backup thread isn't running
         */
        NOT_RUNNING,

        /**
         * The backup thread is compressing the files to be backed up.
         */
        COMPRESSING,

        /**
         * The backup thread is uploading the files
         */
        UPLOADING
    }

    /**
     * List of {@code Uploaders} to upload the backups to
     */
    private ArrayList<Uploader> uploaders;
    /**
     * List of locations to be pruned that were successfully backed up.
     */
    private final Map<String, LocalDateTimeFormatter> locationsToBePruned = new HashMap<>(10);

    /**
     * The list of items to be backed up by the backup thread.
     */
    private List<BackupListEntry> backupList;

    /**
     * The {@code BackupStatus} of the backup thread
     */
    private static BackupStatus backupStatus = BackupStatus.NOT_RUNNING;
    
    private static LocalDateTime nextIntervalBackupTime;
    private static boolean lastBackupSuccessful = true;

    /**
     * The backup currently being backed up by the 
     */
    private static int backupBackingUp;
    
    public abstract static class UploadLogger implements Logger {
        public void broadcast(String input, String... placeholders) {
            MessageUtil.Builder()
                .mmText(input, placeholders)
                .all()
                .send();
        }

        public abstract void log(String input, String... placeholders);
        
        public void initiatorError(String input, String... placeholders) {}

        public void info(String input, String... placeholders) {
            MessageUtil.Builder()
                .mmText(input, placeholders)
                .send();
        }
    }

    /**
     * Creates an instance of the {@code UploadThread} object
     */
    public UploadThread() {
        logger = new UploadLogger() {
            @Override
            public void log(String input, String... placeholders) {
                MessageUtil.Builder()
                    .mmText(input, placeholders)
                    .toPerm(Permission.BACKUP)
                    .send();
            }
        };
        fileUtil = new FileUtil(logger);
    }

    /**
     * Creates an instance of the {@code UploadThread} object
     * @param initiator the player who initiated the backup
     */
    public UploadThread(CommandSender initiator) {
        this.initiator = initiator;
        logger = new UploadLogger() {
            @Override
            public void log(String input, String... placeholders) {
                MessageUtil.Builder()
                    .mmText(input, placeholders)
                    .to(initiator)
                    .toPerm(Permission.BACKUP)
                    .send();
            }
            @Override
            public void initiatorError(String input, String... placeholders) {
                MessageUtil.Builder()
                    .mmText(input, placeholders)
                    .to(initiator)
                    .toConsole(false)
                    .send();
            }
        };
        fileUtil = new FileUtil(logger);
    }

    /**
     * Starts a backup
     */
    @Override
    public void run() {
        if (!locationsToBePruned.isEmpty()) {
            locationsToBePruned.clear();
        }
        Config config = ConfigParser.getConfig();
        if (initiator != null && backupStatus != BackupStatus.NOT_RUNNING) {
            logger.initiatorError(
                intl("backup-already-running"), 
                "backup-status", getBackupStatus());
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
            return;
        }
        boolean errorOccurred = false;
        List<ExternalBackupSource> externalBackupList = Arrays.asList(config.externalBackups.sources);
        backupList = new ArrayList<BackupListEntry>(Arrays.asList(config.backupList.list));
        if (externalBackupList.isEmpty() && backupList.isEmpty()) {
            logger.log(intl("backup-empty-list"));
            return;
        }
        logger.broadcast(intl("backup-start"));
        for (ExternalBackupSource externalBackup : externalBackupList) {
            if (externalBackup instanceof ExternalFTPSource) {
                makeExternalFileBackup((ExternalFTPSource) externalBackup);
            } else {
                makeExternalDatabaseBackup((ExternalMySQLSource) externalBackup);
            }
        }
        logger.log(intl("backup-local-start"));
        backupStatus = BackupStatus.COMPRESSING;
        backupBackingUp = 0;
        ServerUtil.setAutoSave(false);
        for (BackupListEntry set : backupList) {
            for(Path folder : set.location.getPaths()) {
                if (set.create) {
                    makeBackupFile(folder.toString(), set.formatter, Arrays.asList(set.blacklist));
                }
            }
            backupBackingUp++;
        }
        ServerUtil.setAutoSave(true);
        logger.log(intl("backup-local-complete"));
        logger.log(intl("backup-upload-start"));
        backupStatus = BackupStatus.UPLOADING;
        uploaders = new ArrayList<>(5);
        if (config.backupMethods.googleDrive.enabled) {
            uploaders.add(new GoogleDriveUploader(logger));
        }
        if (config.backupMethods.oneDrive.enabled) {
            uploaders.add(new OneDriveUploader(logger));
        }
        if (config.backupMethods.dropbox.enabled) {
            uploaders.add(new DropboxUploader(logger));
        }
        if (config.backupMethods.webdav.enabled) {
            uploaders.add(new WebDAVUploader(logger, config.backupMethods.webdav));
        }
        if (config.backupMethods.nextcloud.enabled) {
            uploaders.add(new NextcloudUploader(logger, config.backupMethods.nextcloud));
        }
        if (config.backupMethods.s3.enabled) {
            uploaders.add(new S3Uploader(logger, config.backupMethods.s3));
        }
        if (config.backupMethods.ftp.enabled) {
            uploaders.add(new FTPUploader(logger, config.backupMethods.ftp));
        }
        if (uploaders.isEmpty() && config.backupStorage.localKeepCount == 0) {
            logger.log(intl("backup-no-methods"));
            return;
        }
        ensureMethodsAuthenticated();
        uploadBackupFiles(uploaders);
        FileUtil.deleteFolder(new File("external-backups"));
        logger.log(intl("backup-upload-complete"));
        backupStatus = BackupStatus.NOT_RUNNING;
        logger.log(intl("upload-error-check"));
        for (Uploader uploader : uploaders) {
            uploader.close();
            if (uploader.isErrorWhileUploading()) {
                logger.log(intl("backup-method-error-occurred"),
                    "diagnose-command", "/drivebackup test " + uploader.getId(),
                    "upload-method", uploader.getName());
                errorOccurred = true;
            } else {
                logger.log(intl("backup-method-complete"),
                    "upload-method", uploader.getName());
            }
        }
        if (!errorOccurred) {
            logger.log(intl("upload-no-errors"));
        }
        logger.broadcast(intl("backup-complete"));
        if (initiator == null) {
            logger.broadcast(getNextAutoBackup());
        }
        if (config.backupStorage.backupsRequirePlayers && Bukkit.getOnlinePlayers().isEmpty() && PlayerListener.isAutoBackupsActive()) {
            logger.info(intl("backup-disabled-inactivity"));
            PlayerListener.setAutoBackupsActive(false);
        }
        lastBackupSuccessful = !errorOccurred;
        pruneLocalBackups();
        if (errorOccurred) {
            DriveBackupApi.backupError();
        } else {
            DriveBackupApi.backupDone();
        }
    }

    private void ensureMethodsAuthenticated() {
        Iterator<Uploader> iterator = uploaders.iterator();
        while (iterator.hasNext()) {
            Uploader uploader = iterator.next();
            AuthenticationProvider provider = uploader.getAuthProvider();
            if (provider != null && !Authenticator.hasRefreshToken(provider)) {
                logger.log(
                    intl("backup-method-not-linked"),
                    "link-command", LINK_COMMAND + provider.getId(),
                    "upload-method", provider.getName());
                iterator.remove();
                continue;
            }
            if (!uploader.isAuthenticated()) {
                if (provider == null) {
                    logger.log(
                        intl("backup-method-not-auth"),
                        "upload-method", uploader.getName());
                } else {
                    logger.log(
                        intl("backup-method-not-auth-authenticator"),
                        "link-command", LINK_COMMAND + provider.getId(),
                        "upload-method", uploader.getName());
                }
                iterator.remove();
            }
        }
    }
    
    private void pruneLocalBackups() {
        logger.log(intl("backup-local-prune-start"));
        for (Map.Entry<String, LocalDateTimeFormatter> entry : locationsToBePruned.entrySet()) {
            String location = entry.getKey();
            LocalDateTimeFormatter formatter = entry.getValue();
            fileUtil.pruneLocalBackups(location, formatter);
        }
        logger.log(intl("backup-local-prune-complete"));
    }
    
    /**
     * Creates a backup file of the specified folder
     * @param location path to the folder
     * @param formatter save format configuration
     * @param blackList a configured blacklist (with globs)
     */
    private void makeBackupFile(String location, LocalDateTimeFormatter formatter, List<String> blackList) {
        logger.info(intl("backup-local-file-start"), "location", location);
        try {
            ServerUtil.setAutoSave(false);
            fileUtil.makeBackup(location, formatter, blackList);
        } catch (IllegalArgumentException exception) {
            logger.log(intl("backup-failed-absolute-path"));
            return;
        } catch (SecurityException exception) {
            logger.log(intl("local-backup-failed-permissions"));
            return;
        } catch (Exception exception) {
            logger.log(intl("backup-local-failed"));
            return;
        }
        locationsToBePruned.put(location, formatter);
        logger.info(intl("backup-local-file-complete"), "location", location);
    }
    
    private void uploadBackupFiles(List<Uploader> uploaders) {
        for (BackupListEntry set : backupList) {
            for(Path folder : set.location.getPaths()) {
                uploadFile(folder.toString(), set.formatter, uploaders);
            }
        }
    }
    
    /**
     * Uploads the most recent backup file to the specified uploaders
     * @param location path to the folder
     * @param formatter save format configuration
     * @param uploaders services to upload to
     */
    private void uploadFile(String location, LocalDateTimeFormatter formatter, List<Uploader> uploaders) {
        try {
            if (FileUtil.isBaseFolder(location)) {
                location = "root";
            }
            TreeMap<Long, File> localBackups = fileUtil.getLocalBackups(location, formatter);
            if (localBackups.isEmpty()) {
                logger.log(intl("location-empty"), "location", location);
                return;
            }
            File file = localBackups.descendingMap().firstEntry().getValue();
            String name = file.getParent().replace("\\", "/").replace("./", "") + "/" + file.getName();
            logger.log(intl("backup-file-upload-start"), "file-name", name);
            Timer timer = new Timer();
            for (Uploader uploader : uploaders) {
                logger.info(
                        intl("backup-method-uploading"),
                        "upload-method",
                        uploader.getName());
                timer.start();
                uploader.uploadFile(file, location);
                timer.end();
                if (!uploader.isErrorWhileUploading()) {
                    logger.info(timer.getUploadTimeMessage(file));
                } else {
                    logger.info(intl("backup-method-upload-failed"));
                }
            }
            logger.log(intl("backup-file-upload-complete"), "file-name", file.getName());
        } catch (Exception e) {
            logger.info(intl("backup-method-upload-failed"));
            MessageUtil.sendConsoleException(e);
        }
    }

    /**
     * Downloads files from an FTP server and stores them within the external-backups temporary folder, using the specified external backup settings.
     * @param externalBackup the external backup settings
     */
    private void makeExternalFileBackup(ExternalFTPSource externalBackup) {
        logger.info(
            intl("external-ftp-backup-start"), 
            "socket-addr", getSocketAddress(externalBackup));
        FTPUploader ftpUploader = new FTPUploader(
                logger,
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
            if (Strings.isNullOrEmpty(externalBackup.baseDirectory)) {
                baseDirectory = backup.path;
            } else {
                baseDirectory = externalBackup.baseDirectory + "/" + backup.path;
            }
            for (String relativeFilePath : ftpUploader.getFiles(baseDirectory)) {
                String filePath = baseDirectory + "/" + relativeFilePath;

                for (BlacklistEntry blacklistEntry : blacklist) {
                    if (blacklistEntry.getPathMatcher().matches(Paths.get(relativeFilePath))) {
                        blacklistEntry.incBlacklistedFiles();
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
                    logger.log(
                        intl("external-ftp-backup-blacklisted"), 
                        "blacklisted-files", String.valueOf(blacklistedFiles),
                        "glob-pattern", globPattern);
                }
            }
        }
        ftpUploader.close();
        BackupListEntry backup = new BackupListEntry(
            new PathBackupLocation("external-backups" + "/" + getTempFolderName(externalBackup)),
            externalBackup.format,
            true,
            new String[0]
        );
        backupList.add(backup);
        if (ftpUploader.isErrorWhileUploading()) {
            logger.log(
                intl("external-ftp-backup-failed"),
                "socket-addr", getSocketAddress(externalBackup));
        } else {
            logger.info(
                intl("external-ftp-backup-complete"),
                "socket-addr", getSocketAddress(externalBackup));
        }
    }

    /**
     * Downloads databases from a MySQL server and stores them within the external-backups temporary folder, using the specified external backup settings.
     * @param externalBackup the external backup settings
     */
    private void makeExternalDatabaseBackup(ExternalMySQLSource externalBackup) {
        logger.info(
            intl("external-mysql-backup-start"), 
            "socket-addr", getSocketAddress(externalBackup));
        MySQLUploader mysqlUploader = new MySQLUploader(
                externalBackup.hostname, 
                externalBackup.port, 
                externalBackup.username, 
                externalBackup.password,
                externalBackup.ssl);
        for (MySQLDatabaseBackup database : externalBackup.databaseList) {
            for (String blacklistEntry : database.blacklist) {
                logger.log(
                    intl("external-mysql-backup-blacklisted"), 
                    "blacklist-entry", blacklistEntry);
            }
            mysqlUploader.downloadDatabase(database.name, getTempFolderName(externalBackup), Arrays.asList(database.blacklist));
        }
        BackupListEntry backup = new BackupListEntry(
            new PathBackupLocation("external-backups" + "/" + getTempFolderName(externalBackup)),
            externalBackup.format,
            true,
            new String[0]
        );
        backupList.add(backup);
        if (mysqlUploader.isErrorWhileUploading()) {
            logger.log(
                intl("external-mysql-backup-failed"), 
                "socket-addr", getSocketAddress(externalBackup));
        } else {
            logger.info(
                intl("external-mysql-backup-complete"),
                "socket-addr", getSocketAddress(externalBackup));
        }
    }

    /**
     * Gets the current status of the backup thread
     * @return the status of the backup thread as a {@code String}
     */
    public static String getBackupStatus() {
        Config config = ConfigParser.getConfig();
        String message;
        switch (backupStatus) {
            case COMPRESSING:
                message = intl("backup-status-compressing");
                break;
            case UPLOADING:
                message = intl("backup-status-uploading");
                break;
            default:
                return intl("backup-status-not-running");
        }
        BackupListEntry[] backupList = config.backupList.list;
        String backupSetName = backupList[backupBackingUp].location.toString();
        return message
            .replace("<set-name>", backupSetName)
            .replace("<set-num>", String.valueOf(backupBackingUp + 1))
            .replace("<set-count>", String.valueOf(backupList.length));
    }

    /**
     * Gets the date/time of the next automatic backup, if enabled.
     * @return the time and/or date of the next automatic backup formatted using the messages in the {@code config.yml} 
     */
    public static String getNextAutoBackup() {
        Config config = ConfigParser.getConfig();
        if (config.backupScheduling.enabled) {
            long now = ZonedDateTime.now(config.advanced.dateTimezone).toEpochSecond();
            ZonedDateTime nextBackupDate = Collections.min(Scheduler.getBackupDatesList(), new Comparator<ZonedDateTime>() {
                public int compare(ZonedDateTime d1, ZonedDateTime d2) {
                    long diff1 = Math.abs(d1.toEpochSecond() - now);
                    long diff2 = Math.abs(d2.toEpochSecond() - now);
                    return Long.compare(diff1, diff2);
                }
            });
            DateTimeFormatter backupDateFormatter = DateTimeFormatter.ofPattern(intl("next-schedule-backup-format"), config.advanced.dateLanguage);
            return intl("next-schedule-backup").replaceAll("%DATE", nextBackupDate.format(backupDateFormatter));
        } else if (config.backupStorage.delay != -1) {
            return intl("next-backup").replaceAll("%TIME", String.valueOf(LocalDateTime.now().until(nextIntervalBackupTime, ChronoUnit.MINUTES)));
        } else {
            return intl("auto-backups-disabled");
        }
    }

    /**
     * Sets the time of the next interval-based backup to the current time + the configured interval.
     */
    public static void updateNextIntervalBackupTime() {
        nextIntervalBackupTime = LocalDateTime.now().plus(ConfigParser.getConfig().backupStorage.delay, ChronoUnit.MINUTES);
    }

    public static boolean wasLastBackupSuccessful() {
        return lastBackupSuccessful;
    }

    /**
     * Gets the socket address (ipaddress/hostname:port) of an external backup server based on the specified settings.
     * @param externalBackup the external backup settings
     * @return the socket address
     */
    @NotNull
    @Contract (pure = true)
    private static String getSocketAddress(@NotNull ExternalBackupSource externalBackup) {
        return externalBackup.hostname + ":" + externalBackup.port;
    }

    /**
     * Generates the name for a folder based on the specified external backup settings to be stored within the external-backups temporary folder.
     * @param externalBackup the external backup settings
     * @return the folder name
     */
    @NotNull
    private static String getTempFolderName(ExternalBackupSource externalBackup) {
        if (externalBackup instanceof ExternalFTPSource) {
            return "ftp-" + getSocketAddress(externalBackup);
        } else {
            return "mysql-" + getSocketAddress(externalBackup);
        }
    }
}

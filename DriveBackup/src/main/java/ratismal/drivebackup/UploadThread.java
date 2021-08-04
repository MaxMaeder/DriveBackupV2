package ratismal.drivebackup;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import ratismal.drivebackup.uploaders.Authenticator;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.uploaders.Authenticator.AuthenticationProvider;
import ratismal.drivebackup.uploaders.dropbox.DropboxUploader;
import ratismal.drivebackup.uploaders.ftp.FTPUploader;
import ratismal.drivebackup.uploaders.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.uploaders.onedrive.OneDriveUploader;
import ratismal.drivebackup.uploaders.mysql.MySQLUploader;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.Permissions;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.config.configSections.BackupList.BackupListEntry;
import ratismal.drivebackup.config.configSections.BackupList.BackupListEntry.PathBackupLocation;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalBackupSource;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalFTPSource;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalMySQLSource;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalFTPSource.ExternalBackupListEntry;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalMySQLSource.MySQLDatabaseBackup;
import ratismal.drivebackup.handler.PlayerListener;
import ratismal.drivebackup.plugin.Scheduler;
import ratismal.drivebackup.util.*;
import ratismal.drivebackup.util.Timer;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import com.google.api.client.util.Strings;

import static ratismal.drivebackup.config.Localization.intl;

/**
 * Created by Ratismal on 2016-01-22.
 */

public class UploadThread implements Runnable {
    private CommandSender initiator;
    private UploadLogger logger;

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
     * List of {@code Uploaders} to upload the backups to
     */
    private ArrayList<Uploader> uploaders;

    /**
     * The list of items to be backed up by the backup thread
     */
    private static List<BackupListEntry> backupList;

    private static LocalDateTime nextIntervalBackupTime = null;

    /**
     * The {@code BackupStatus} of the backup thread
     */
    private static BackupStatus backupStatus = BackupStatus.NOT_RUNNING;

    /**
     * The backup currently being backed up by the 
     */
    private static int backupBackingUp = 0;

    public static abstract class UploadLogger implements Logger {
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
                    .toPerm(Permissions.BACKUP)
                    .send();
            }
        };
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
                    .toPerm(Permissions.BACKUP)
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
    }

    /**
     * Starts a backup
     */
    @Override
    public void run() {
        Config config = ConfigParser.getConfig();

        if (initiator != null && backupStatus != BackupStatus.NOT_RUNNING) {
            logger.initiatorError(
                intl("backup-already-running"), 
                "backup-status", getBackupStatus());;

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
            logger.log(intl("backup-skipped-inactivity"));

            return;
        }

        boolean errorOccurred = false;

        if (
            !config.backupMethods.googleDrive.enabled && 
            !config.backupMethods.oneDrive.enabled && 
            !config.backupMethods.dropbox.enabled && 
            !config.backupMethods.ftp.enabled && 
            config.backupStorage.localKeepCount == 0
            ) {

            logger.log(intl("backup-no-methods"));

            return;
        }

        List<ExternalBackupSource> externalBackupList = Arrays.asList(config.externalBackups.sources);
        backupList = Arrays.asList(config.backupList.list);

        if (externalBackupList.size() == 0 && backupList.size() == 0) {
            logger.log(intl("backup-empty-list"));
            return;
        }

        ServerUtil.setAutoSave(false);

        logger.broadcast(intl("backup-start"));

        uploaders = new ArrayList<Uploader>();
        if (config.backupMethods.googleDrive.enabled) {
            uploaders.add(new GoogleDriveUploader(logger));
        }
        if (config.backupMethods.oneDrive.enabled) {
            uploaders.add(new OneDriveUploader(logger));
        }
        if (config.backupMethods.dropbox.enabled) {
            uploaders.add(new DropboxUploader(logger));
        }
        if (config.backupMethods.ftp.enabled) {
            uploaders.add(new FTPUploader());
        }

        ensureMethodsLinked();

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
                doSingleBackup(folder.toString(), set.formatter, set.create, Arrays.asList(set.blacklist), uploaders);
            }

            backupBackingUp++;
        }

        FileUtil.deleteFolder(new File("external-backups"));

        backupStatus = BackupStatus.NOT_RUNNING;

        for (Uploader uploader : uploaders) {
            uploader.close();

            if (uploader.isErrorWhileUploading()) {
                logger.log(
                    intl("backup-method-error-occurred"),
                    "diagnose-command", "/drivebackup test " + uploader.getAuthProvider().getId(),
                    "backup-method", uploader.getName());

                errorOccurred = true;
            } else {
                logger.log(
                    intl("backup-method-complete"), 
                    "upload-method", uploader.getName());
            }
        }

        if (initiator != null) {
            logger.broadcast(intl("backup-complete"));
        } else {
            logger.broadcast(intl("backup-complete") + " " + getNextAutoBackup());
        }

        if (config.backupStorage.backupsRequirePlayers && Bukkit.getOnlinePlayers().size() == 0 && PlayerListener.isAutoBackupsActive()) {
            logger.info(intl("backup-disabled-inactivity"));
            PlayerListener.setAutoBackupsActive(false);
        }

        ServerUtil.setAutoSave(true);

        if (errorOccurred) {
            DriveBackupApi.backupError();
        } else {
            DriveBackupApi.backupDone();
        }
    }

    private void ensureMethodsLinked() {
        for (Uploader uploader : uploaders) {
            AuthenticationProvider provider = uploader.getAuthProvider();
            if (provider == null) continue;

            if (!Authenticator.hasRefreshToken(provider)) {
                logger.log(
                    intl("backup-method-not-linked"),
                    "link-command", "/drivebackup linkaccount " + provider.getId().replace("-", ""),
                    "upload-method", provider.getName());

                uploaders.remove(uploader);
            }
        }
    }

    /**
     * Backs up a single backup location
     * @param location Path to the folder
     * @param formatter Save format configuration
     * @param create Create the zip file or just upload it? ("True" / "False")
     * @param blackList configured blacklist (with globs)
     * @param uploaders All services to upload to
     * @return True if any error occurred
     */
    private void doSingleBackup(String location, LocalDateTimeFormatter formatter, boolean create, List<String> blackList, List<Uploader> uploaders) {
        logger.info(intl("backup-location-start"), "location", location);

        FileUtil fileUtil = new FileUtil(logger);

        if (create) {
            backupStatus = BackupStatus.COMPRESSING;

            try {
                fileUtil.makeBackup(location, formatter, blackList);
            } catch (IllegalArgumentException exception) {
                logger.log(intl("backup-failed-absolute-path"));

                return;
            } catch (Exception exception) {
                logger.log(intl("backup-local-failed"));

                return;
            }
        }

        try {
            backupStatus = BackupStatus.UPLOADING;

            if (FileUtil.isBaseFolder(location)) {
                location = "root";
            }

            File file = fileUtil
                            .getLocalBackups(location, formatter)
                            .descendingMap().firstEntry().getValue();
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

            fileUtil.pruneLocalBackups(location, formatter);
        } catch (Exception e) {

            logger.info(intl("backup-method-upload-failed"));
            MessageUtil.sendConsoleException(e);
        }
    }

    /**
     * Downloads files from a FTP server and stores them within the external-backups temporary folder, using the specified external backup settings
     * @param externalBackup the external backup settings
     */
    private void makeExternalFileBackup(ExternalFTPSource externalBackup) {
        logger.info(
            intl("external-ftp-backup-start"), 
            "socked-addr", getSocketAddress(externalBackup));

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
                "socked-addr", getSocketAddress(externalBackup));
        } else {
            logger.info(
                intl("external-ftp-backup-complete"),
                "socked-addr", getSocketAddress(externalBackup));
        }
    }

    /**
     * Downloads databases from a MySQL server and stores them within the external-backups temporary folder, using the specified external backup settings
     * @param externalBackup the external backup settings
     */
    private void makeExternalDatabaseBackup(ExternalMySQLSource externalBackup) {
        logger.info(
            intl("external-mysql-backup-start"), 
            "socked-addr", getSocketAddress(externalBackup));

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
                "socked-addr", getSocketAddress(externalBackup));
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
     * Gets the date/time of the next automatic backup, if enabled
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
}

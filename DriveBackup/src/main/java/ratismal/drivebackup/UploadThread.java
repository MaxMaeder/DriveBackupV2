package ratismal.drivebackup;

import com.google.api.client.util.Strings;
import org.bukkit.Bukkit;
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
import ratismal.drivebackup.configuration.ConfigurationObject;
import ratismal.drivebackup.constants.BackupStatusValue;
import ratismal.drivebackup.constants.Initiator;
import ratismal.drivebackup.handler.BackupStatus;
import ratismal.drivebackup.handler.listeners.PlayerListener;
import ratismal.drivebackup.objects.Player;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.plugin.Scheduler;
import ratismal.drivebackup.uploaders.AuthenticationProvider;
import ratismal.drivebackup.uploaders.Authenticator;
import ratismal.drivebackup.uploaders.UploadLogger;
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
import ratismal.drivebackup.util.ServerUtil;
import ratismal.drivebackup.util.Timer;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

/**
 * Created by Ratismal on 2016-01-22.
 */

public final class UploadThread implements Runnable {
    
    private static final String LINK_COMMAND = "/drivebackup linkaccount ";
    private DriveBackupInstance instance;
    private Initiator initiator;
    private UploadLogger uploadLogger;
    private FileUtil fileUtil;
    private Timer totalTimer;
    
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
    
    private static LocalDateTime nextIntervalBackupTime;
    private static boolean lastBackupSuccessful = true;

    /**
     * The backup currently being backed up by the
     */
    private static int backupBackingUp;

    /**
     * Creates an instance of the {@code UploadThread} object
     * Used for automated backups
     * @param instance the instance of the plugin
     */
    public UploadThread(DriveBackupInstance instance) {
        setup(instance, Initiator.AUTOMATIC);
    }
    
    /**
     * Creates an instance of the {@code UploadThread} object
     * used when a player initiates a backup
     * @param instance the instance of the plugin
     * @param player the player who initiated the backup
     */
    public UploadThread(DriveBackupInstance instance, Player player) {
        uploadLogger = new UploadLogger(instance, player);
        setup(instance, Initiator.PLAYER);
    }

    /**
     * Creates an instance of the {@code UploadThread} object
     * used when a non-player initiates a backup
     * @param instance the instance of the plugin
     * @param initiator the initiator of the backup that isn't a player
     */
    public UploadThread(DriveBackupInstance instance, Initiator initiator) {
        if (Initiator.PLAYER == initiator) {
            throw new IllegalArgumentException("initiator cannot be a player, use the other constructor instead");
        }
        setup(instance, initiator);
    }
    
    private void setup(DriveBackupInstance instance, Initiator initiator) {
        this.initiator = initiator;
        this.instance = instance;
        if (uploadLogger == null) {
            uploadLogger = new UploadLogger(instance, initiator);
        }
        fileUtil = new FileUtil(instance, uploadLogger);
        totalTimer = new Timer(instance);
    }

    /**
     * Starts a backup
     */
    @Override
    public void run() {
        if (initiator != null && BackupStatusValue.NOT_RUNNING != BackupStatus.getStatus()) {
            uploadLogger.info("backup-already-running", "backup-status", getBackupStatus());
            return;
        }
        try {
            run_internal();
        } catch (Exception e) {
            lastBackupSuccessful = false;
            throw e;
        } finally {
            BackupStatus.setStatus(BackupStatusValue.NOT_RUNNING);
            if (lastBackupSuccessful) {
                instance.getAPIHandler().backupError();
            } else {
                instance.getAPIHandler().backupDone();
            }
        }
    }

    /**
     * actual backup logic
     */
    void run_internal() {
        Config config = ConfigParser.getConfig();
        BackupStatus.setStatus(BackupStatusValue.STARTING);
        totalTimer.start();
        if (!locationsToBePruned.isEmpty()) {
            locationsToBePruned.clear();
        }
        Thread.currentThread().setPriority(config.backupStorage.threadPriority);
        if (initiator.isAuto()) {
            updateNextIntervalBackupTime();
        }
        if (!instance.getAPIHandler().shouldStartBackup()) {
            return;
        }
        if (config.backupStorage.backupsRequirePlayers && !PlayerListener.isAutoBackupsActive() && initiator == null) {
            return;
        }
        if (!locationsToBePruned.isEmpty()) {
            locationsToBePruned.clear();
        }
        boolean errorOccurred = false;
        List<ExternalBackupSource> externalBackupList = Arrays.asList(config.externalBackups.sources);
        backupList = new ArrayList<>(Arrays.asList(config.backupList.list));
        if (externalBackupList.isEmpty() && backupList.isEmpty()) {
            uploadLogger.log("backup-empty-list");
            return;
        }
        uploadLogger.broadcast("backup-start");
        for (ExternalBackupSource externalBackup : externalBackupList) {
            if (externalBackup instanceof ExternalFTPSource) {
                makeExternalFileBackup((ExternalFTPSource) externalBackup);
            } else {
                makeExternalDatabaseBackup((ExternalMySQLSource) externalBackup);
            }
        }
        uploadLogger.log("backup-local-start");
        BackupStatus.setStatus(BackupStatusValue.COMPRESSING);
        backupBackingUp = 0;
        try {
            instance.disableWorldAutoSave();
        } catch (Exception e) {
            uploadLogger.log("auto-save-disable-fail");
        }
        for (BackupListEntry set : backupList) {
            backupBackingUp++;
            for (Path folder : set.location.getPaths()) {
                if (set.create) {
                    makeBackupFile(folder.toString(), set.formatter, Arrays.asList(set.blacklist));
                }
            }
        }
        try {
            instance.enableWorldAutoSave();
        } catch (Exception e) {
            uploadLogger.log("auto-save-enable-fail");
        }
        uploadLogger.log("backup-local-complete");
        uploadLogger.log("backup-upload-start");
        BackupStatus.setStatus(BackupStatusValue.UPLOADING);
        uploaders = new ArrayList<>(5);
        if (config.backupMethods.googleDrive.enabled) {
            uploaders.add(new GoogleDriveUploader(instance, uploadLogger));
        }
        if (config.backupMethods.oneDrive.enabled) {
            uploaders.add(new OneDriveUploader(instance, uploadLogger));
        }
        if (config.backupMethods.dropbox.enabled) {
            uploaders.add(new DropboxUploader(instance, uploadLogger));
        }
        if (config.backupMethods.webdav.enabled) {
            uploaders.add(new WebDAVUploader(instance, uploadLogger, config.backupMethods.webdav));
        }
        if (config.backupMethods.nextcloud.enabled) {
            uploaders.add(new NextcloudUploader(instance, uploadLogger, config.backupMethods.nextcloud));
        }
        if (config.backupMethods.s3.enabled) {
            uploaders.add(new S3Uploader(instance, uploadLogger, config.backupMethods.s3));
        }
        if (config.backupMethods.ftp.enabled) {
            uploaders.add(new FTPUploader(instance, uploadLogger, config.backupMethods.ftp));
        }
        if (uploaders.isEmpty() && config.backupStorage.localKeepCount == 0) {
            uploadLogger.log("backup-no-methods");
            return;
        }
        ensureMethodsAuthenticated();
        uploadBackupFiles(uploaders);
        FileUtil.deleteFolder(new File("external-backups"));
        uploadLogger.log("backup-upload-complete");
        BackupStatus.setStatus(BackupStatusValue.NOT_RUNNING);
        uploadLogger.log("upload-error-check");
        for (Uploader uploader : uploaders) {
            uploader.close();
            if (uploader.didErrorOccur()) {
                Map<String, String> placeholders = new HashMap<>(2);
                placeholders.put("upload-method", uploader.getName());
                placeholders.put("diagnose-command", "/drivebackup test " + uploader.getId());
                uploadLogger.log("backup-method-error-occurred", placeholders);
                errorOccurred = true;
            } else {
                uploadLogger.log("backup-method-complete", "upload-method", uploader.getName());
            }
        }
        if (!errorOccurred) {
            uploadLogger.log("upload-no-errors");
        }
        uploadLogger.broadcast("backup-complete");
        if (initiator.isAuto()) {
            uploadLogger.broadcast(getNextAutoBackup(instance));
        }
        if (config.backupStorage.backupsRequirePlayers && Bukkit.getOnlinePlayers().isEmpty() && PlayerListener.isAutoBackupsActive()) {
            uploadLogger.info("backup-disabled-inactivity");
            PlayerListener.setAutoBackupsActive(false);
        }
        setLastBackupSuccessful(!errorOccurred);
        BackupStatus.setStatus(BackupStatusValue.PRUNING);
        pruneLocalBackups();
        totalTimer.end();
        long totalBackupTime = totalTimer.getTime();
        long totalSeconds = Duration.of(totalBackupTime, ChronoUnit.MILLIS).getSeconds();
        uploadLogger.log("backup-total-time", "time", String.valueOf(totalSeconds));
        BackupStatus.setStatus(BackupStatusValue.NOT_RUNNING);
    }
    
    @Contract (mutates = "this")
    private void setLastBackupSuccessful(boolean lastBackupSuccessful) {
        this.lastBackupSuccessful = lastBackupSuccessful;
    }
    
    private void ensureMethodsAuthenticated() {
        Iterator<Uploader> iterator = uploaders.iterator();
        while (iterator.hasNext()) {
            Uploader uploader = iterator.next();
            AuthenticationProvider provider = uploader.getAuthProvider();
            if (provider != null && !Authenticator.hasRefreshToken(provider)) {
                Map<String, String> placeholders = new HashMap<>(2);
                placeholders.put("upload-method", uploader.getName());
                placeholders.put("link-command", LINK_COMMAND + provider.getId());
                uploadLogger.log("backup-method-not-linked", placeholders);
                iterator.remove();
                continue;
            }
            if (!uploader.isAuthenticated()) {
                if (provider == null) {
                    uploadLogger.log("backup-method-not-auth", "upload-method", uploader.getName());
                } else {
                    Map<String, String> placeholders = new HashMap<>(2);
                    placeholders.put("upload-method", uploader.getName());
                    placeholders.put("link-command", LINK_COMMAND + provider.getId());
                    uploadLogger.log("backup-method-not-auth-authenticator", placeholders);
                }
                iterator.remove();
            }
        }
    }
    
    private void pruneLocalBackups() {
        uploadLogger.log("backup-local-prune-start");
        for (Map.Entry<String, LocalDateTimeFormatter> entry : locationsToBePruned.entrySet()) {
            String location = entry.getKey();
            LocalDateTimeFormatter formatter = entry.getValue();
            fileUtil.pruneLocalBackups(location, formatter);
        }
        uploadLogger.log("backup-local-prune-complete");
    }
    
    /**
     * Creates a backup file of the specified folder
     * @param location path to the folder
     * @param formatter save format configuration
     * @param blackList a configured blacklist (with globs)
     */
    private void makeBackupFile(String location, LocalDateTimeFormatter formatter, List<String> blackList) {
        uploadLogger.info("backup-local-file-start", "location", location);
        try {
            ServerUtil.setAutoSave(false);
            fileUtil.makeBackup(location, formatter, blackList);
        } catch (IllegalArgumentException exception) {
            uploadLogger.log("backup-failed-absolute-path");
            return;
        } catch (SecurityException exception) {
            uploadLogger.log("local-backup-failed-permissions");
            return;
        } catch (Exception exception) {
            uploadLogger.log("backup-local-failed");
            return;
        }
        locationsToBePruned.put(location, formatter);
        uploadLogger.info("backup-local-file-complete", "location", location);
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
    private void uploadFile(String location, LocalDateTimeFormatter formatter, Iterable<Uploader> uploaders) {
        try {
            if (FileUtil.isBaseFolder(location)) {
                location = "root";
            }
            TreeMap<Long, File> localBackups = fileUtil.getLocalBackups(location, formatter);
            if (localBackups.isEmpty()) {
                uploadLogger.log("location-empty", "location", location);
                return;
            }
            File file = localBackups.descendingMap().firstEntry().getValue();
            String name = file.getParent().replace("\\", "/").replace("./", "") + "/" + file.getName();
            uploadLogger.log("backup-file-upload-start", "file-name", name);
            Timer timer = new Timer(instance);
            for (Uploader uploader : uploaders) {
                uploadLogger.info("backup-method-uploading", "upload-method", uploader.getName());
                timer.start();
                uploader.uploadFile(file, location);
                timer.end();
                if (uploader.didErrorOccur()) {
                    uploadLogger.info("backup-method-upload-failed");
                } else {
                    uploadLogger.info(timer.getUploadTimeMessage(file));
                }
            }
            uploadLogger.log("backup-file-upload-complete", "file-name", file.getName());
        } catch (Exception e) {
            uploadLogger.log("backup-method-upload-failed", e);
        }
    }

    /**
     * Downloads files from an FTP server and stores them within the external-backups temporary folder, using the specified external backup settings.
     * @param externalBackup the external backup settings
     */
    private void makeExternalFileBackup(ExternalFTPSource externalBackup) {
        uploadLogger.info("external-ftp-backup-start", "socket-addr", getSocketAddress(externalBackup));
        FTPUploader ftpUploader = new FTPUploader(
                instance,
                uploadLogger,
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
            List<BlacklistEntry> blacklist = new ArrayList<>(2);
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
                Path path = Paths.get(relativeFilePath);
                for (BlacklistEntry blacklistEntry : blacklist) {
                    if (blacklistEntry.getPathMatcher().matches(path)) {
                        blacklistEntry.incBlacklistedFiles();
                    }
                }
                String parentFolder = path.toFile().getParent();
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
                    Map<String, String> placeholders = new HashMap<>(2);
                    placeholders.put("blacklisted-files", String.valueOf(blacklistedFiles));
                    placeholders.put("glob-pattern", globPattern);
                    uploadLogger.log("external-ftp-backup-blacklisted", placeholders);
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
        if (ftpUploader.didErrorOccur()) {
            uploadLogger.log("external-ftp-backup-failed", "socket-addr", getSocketAddress(externalBackup));
        } else {
            uploadLogger.info("external-ftp-backup-complete", "socket-addr", getSocketAddress(externalBackup));
        }
    }

    /**
     * Downloads databases from a MySQL server and stores them within the external-backups temporary folder,
     * using the specified external backup settings.
     * @param externalBackup the external backup settings
     */
    private void makeExternalDatabaseBackup(ExternalMySQLSource externalBackup) {
        uploadLogger.info("external-mysql-backup-start", "socket-addr", getSocketAddress(externalBackup));
        MySQLUploader mysqlUploader = new MySQLUploader(instance,
                externalBackup.hostname,
                externalBackup.port,
                externalBackup.username,
                externalBackup.password,
                externalBackup.ssl);
        for (MySQLDatabaseBackup database : externalBackup.databaseList) {
            for (String blacklistEntry : database.blacklist) {
                uploadLogger.log("external-mysql-backup-blacklisted", "blacklist-entry", blacklistEntry);
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
        if (mysqlUploader.didErrorOccur()) {
            uploadLogger.log("external-mysql-backup-failed", "socket-addr", getSocketAddress(externalBackup));
        } else {
            uploadLogger.info("external-mysql-backup-complete", "socket-addr", getSocketAddress(externalBackup));
        }
    }

    /**
     * Gets the current status of the backup thread
     * @return the status of the backup thread as a {@code String}
     */
    public String getBackupStatus() {
        Config config = ConfigParser.getConfig();
        String message;
        switch (BackupStatus.getStatus()) {
            case COMPRESSING:
                message = instance.getMessageHandler().getLangString("backup-status-compressing");
                break;
            case UPLOADING:
                message = instance.getMessageHandler().getLangString("backup-status-uploading");
                break;
            case STARTING:
                message = instance.getMessageHandler().getLangString("backup-status-starting");
                break;
            case PRUNING:
                message = instance.getMessageHandler().getLangString("backup-status-pruning");
                break;
            default:
                return instance.getMessageHandler().getLangString("backup-status-not-running");
        }
        BackupListEntry[] backupList = config.backupList.list;
        int backup = 0;
        //edge case when its in between backup steps where number is set to 0
        int backupNumber = Math.max(0, backupBackingUp-1);
        if (backupNumber <= backupList.length) {
            backup = backupNumber;
        }
        String backupSetName = backupList[backup].location.toString();
        return message
            .replace("<set-name>", backupSetName)
            .replace("<set-num>", String.valueOf(backupNumber+1))
            .replace("<set-count>", String.valueOf(backupList.length));
    }

    /**
     * Gets the date/time of the next automatic backup, if enabled.
     * @return the time and/or date of the next automatic backup formatted using the messages in the {@code config.yml}
     */
    public static String getNextAutoBackup(DriveBackupInstance instance) {
        ConfigurationObject config = instance.getConfigHandler().getConfig();
        if (config.getValue("scheduled-backups").getBoolean()) {
            String offset = instance.getConfigHandler().getConfig().getValue("advanced", "date-timezone").getString();
            ZoneOffset zoneOffset = ZoneOffset.of(offset);
            ZonedDateTime now = ZonedDateTime.now(zoneOffset);
            //TODO remove use of scheduler
            ZonedDateTime nextBackupDate = Scheduler.getBackupDatesList().stream()
                                                    .filter(zdt -> zdt.isAfter(now))
                                                    .min(Comparator.naturalOrder())
                                                    .orElseThrow(NoSuchElementException::new);
            String dateLanguageString = instance.getConfigHandler().getConfig().getValue("advanced", "date-language").getString();
            Locale dateLanguage = Locale.forLanguageTag(dateLanguageString);
            String format = instance.getMessageHandler().getLangString("next-schedule-backup-format");
            DateTimeFormatter backupDateFormatter = DateTimeFormatter.ofPattern(format, dateLanguage);
            String message = instance.getMessageHandler().getLangString("next-schedule-backup");
            return message.replaceAll("%DATE", nextBackupDate.format(backupDateFormatter));
        } else if (config.getValue("delay").getLong() != -1L) {
            String message = instance.getMessageHandler().getLangString("next-backup");
            long nextTime = LocalDateTime.now().until(nextIntervalBackupTime, ChronoUnit.MINUTES);
            return message.replaceAll("%TIME", String.valueOf(nextTime));
        } else {
            return instance.getMessageHandler().getLangString("auto-backups-disabled");
        }
    }

    /**
     * Sets the time of the next interval-based backup to the current time + the configured interval.
     */
    public void updateNextIntervalBackupTime() {
        long delay = instance.getConfigHandler().getConfig().getValue("delay").getLong();
        nextIntervalBackupTime = LocalDateTime.now().plusMinutes(delay);
    }

    @Contract (pure = true)
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

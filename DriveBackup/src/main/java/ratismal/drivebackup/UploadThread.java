package ratismal.drivebackup;

import com.google.api.client.util.Strings;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Contract;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import ratismal.drivebackup.configuration.ConfigurationObject;
import ratismal.drivebackup.constants.BackupStatusValue;
import ratismal.drivebackup.constants.Initiator;
import ratismal.drivebackup.handler.BackupStatus;
import ratismal.drivebackup.handler.logging.LoggingInterface;
import ratismal.drivebackup.handler.player.PlayerHandler;
import ratismal.drivebackup.objects.BackupListEntry;
import ratismal.drivebackup.objects.BackupLocation;
import ratismal.drivebackup.objects.ExternalBackupListEntry;
import ratismal.drivebackup.objects.ExternalBackupSource;
import ratismal.drivebackup.objects.ExternalDatabaseEntry;
import ratismal.drivebackup.objects.ExternalFTPSource;
import ratismal.drivebackup.objects.ExternalMySQLSource;
import ratismal.drivebackup.objects.GlobBackupLocation;
import ratismal.drivebackup.objects.PathBackupLocation;
import ratismal.drivebackup.objects.Player;
import ratismal.drivebackup.platforms.DriveBackupInstance;
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
import ratismal.drivebackup.util.Timer;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

/**
 * Created by Ratismal on 2016-01-22.
 */

public final class UploadThread implements Runnable {
    
    private static final String LINK_COMMAND = "/drivebackup linkaccount ";
    private static final Pattern TIME_PATTERN = Pattern.compile("%TIME");
    private static final Pattern DATE_PATTERN = Pattern.compile("%DATE");
    private DriveBackupInstance instance;
    private Initiator initiator;
    private UploadLogger uploadLogger;
    private FileUtil fileUtil;
    private Timer totalTimer;
    @Setter
    @Getter
    private static Scheduler scheduler;
    
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
    private static List<BackupListEntry> backupList = new ArrayList<>(2);
    private static List<ExternalBackupSource> externalBackupList = new ArrayList<>(2);
    
    private static LocalDateTime nextIntervalBackupTime;
    @Setter(AccessLevel.PRIVATE)
    private static boolean lastBackupSuccessful = true;
    @Getter
    @Setter
    private static boolean autoBackupsActive = true;

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
    
    public static void loadBackupList(DriveBackupInstance instance) {
        LoggingInterface logger = instance.getLoggingHandler().getPrefixedLogger("BackupListLoader");
        CommentedConfigurationNode configNode = instance.getConfigHandler().getConfig().getConfig().node("backup-list");
        List<CommentedConfigurationNode> configList = configNode.childrenList();
        for (CommentedConfigurationNode backupNode : configList) {
            String formatString = backupNode.node("format").getString();
            LocalDateTimeFormatter formatter;
            try {
                formatter = LocalDateTimeFormatter.ofPattern(instance, formatString);
            } catch (IllegalArgumentException e) {
                logger.error("Failed to load format for backup format: " + formatString, e);
                continue;
            }
            boolean create = backupNode.node("create").getBoolean(true);
            String[] blackList = new String[0];
            try {
                List<String> list = backupNode.node("blacklist").getList(String.class);
                if (list != null) {
                    blackList = list.toArray(blackList);
                }
            } catch (SerializationException e) {
                logger.error("Failed to load blacklist for backup format: " + formatString, e);
            }
            BackupLocation backupLocation;
            if (backupNode.node("glob").virtual()) {
                String path = backupNode.node("path").getString();
                backupLocation = new PathBackupLocation(path);
            } else {
                String glob = backupNode.node("glob").getString();
                backupLocation = new GlobBackupLocation(glob);
            }
            BackupListEntry backup = new BackupListEntry(backupLocation, formatter, create, blackList);
            backupList.add(backup);
        }
    }
    
    public static void loadExternalBackupList(DriveBackupInstance instance) {
        LoggingInterface logger = instance.getLoggingHandler().getPrefixedLogger("ExternalBackupListLoader");
        CommentedConfigurationNode configNode = instance.getConfigHandler().getConfig().getConfig().node("external-backup-list");
        List<CommentedConfigurationNode> configList = configNode.childrenList();
        for (CommentedConfigurationNode backupNode : configList) {
            String type = backupNode.node("type").getString();
            String[] validTypes = {"ftpServer", "ftpsServer", "sftpServer", "mysqlDatabase"};
            if (!Arrays.asList(validTypes).contains(type) || type == null || type.isEmpty()) {
                logger.error("Invalid external backup type: " + type);
                continue;
            }
            String hostname = backupNode.node("hostname").getString();
            int port = backupNode.node("port").getInt();
            String username = backupNode.node("username").getString();
            String password = backupNode.node("password").getString();
            String formatString = backupNode.node("format").getString();
            LocalDateTimeFormatter formatter;
            try {
                formatter = LocalDateTimeFormatter.ofPattern(instance, formatString);
            } catch (IllegalArgumentException e) {
                logger.error("Failed to load format for external backup format: " + formatString, e);
                continue;
            }
            switch (type) {
                case "ftpServer":
                case "ftpsServer":
                case "sftpServer":
                    boolean ftps = "ftpsServer".equals(type);
                    boolean sftp = "sftpServer".equals(type);
                    String publicKey = backupNode.node("publicKey").getString("");
                    String passphrase = backupNode.node("passphrase").getString("");
                    String baseDirectory = backupNode.node("baseDirectory").getString("");
                    try {
                        checkPath(publicKey);
                        checkPath(baseDirectory);
                    } catch (InvalidPathException e) {
                        logger.error("Failed to load external backup source: " + type, e);
                        continue;
                    }
                    List<CommentedConfigurationNode> backupList = backupNode.node("backupList").childrenList();
                    List<ExternalBackupListEntry> entries = new ArrayList<>(backupList.size());
                    for (CommentedConfigurationNode backup : backupList) {
                        String path = backup.node("path").getString();
                        String[] blacklist = new String[0];
                        try {
                            List<String> list = backup.node("blacklist").getList(String.class);
                            if (list != null) {
                                blacklist = list.toArray(blacklist);
                            }
                        } catch (SerializationException e) {
                            logger.error("Failed to load blacklist for external backup source: " + type, e);
                        }
                        entries.add(new ExternalBackupListEntry(path, blacklist));
                    }
                    ExternalBackupListEntry[] backupArray = new ExternalBackupListEntry[entries.size()];
                    ExternalFTPSource ftpSource = new ExternalFTPSource(hostname, port, username, password,
                            formatter, sftp, ftps, publicKey, passphrase, baseDirectory, entries.toArray(backupArray));
                    externalBackupList.add(ftpSource);
                    break;
                case "mysqlDatabase":
                    boolean ssl = backupNode.node("ssl").getBoolean(false);
                    List<CommentedConfigurationNode> databaseList = backupNode.node("databaseList").childrenList();
                    List<ExternalDatabaseEntry> databases = new ArrayList<>(databaseList.size());
                    for (CommentedConfigurationNode database : databaseList) {
                        String name = database.node("name").getString();
                        String[] blacklist = new String[0];
                        try {
                            List<String> list = database.node("blacklist").getList(String.class);
                            if (list != null) {
                                blacklist = list.toArray(blacklist);
                            }
                        } catch (SerializationException e) {
                            logger.error("Failed to load blacklist for external database: " + name, e);
                        }
                        databases.add(new ExternalDatabaseEntry(name, blacklist));
                    }
                    ExternalDatabaseEntry[] databaseArray = new ExternalDatabaseEntry[databases.size()];
                    ExternalMySQLSource mysqlSource = new ExternalMySQLSource(hostname, port, username, password,
                            formatter, ssl, databases.toArray(databaseArray));
                    externalBackupList.add(mysqlSource);
            }
        }
    }
    
    private static void checkPath(String path) throws InvalidPathException {
        if (path.contains("\\")) {
            throw new InvalidPathException(path, "Path must use the unix file separator, \"/\"");
        }
    }
    
    private void setup(DriveBackupInstance instance, Initiator initiator) {
        this.initiator = initiator;
        this.instance = instance;
        if (uploadLogger == null) {
            uploadLogger = new UploadLogger(instance, initiator);
        }
        fileUtil = new FileUtil(instance, uploadLogger);
        totalTimer = new Timer(instance);
        if (scheduler == null) {
            scheduler = new ratismal.drivebackup.Scheduler(instance);
        }
        if (backupList.isEmpty()) {
            loadBackupList(instance);
        }
        if (externalBackupList.isEmpty()) {
            loadExternalBackupList(instance);
        }
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
            setLastBackupSuccessful(false);
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
    
    private boolean isMethodEnabled(String method) {
        return instance.getConfigHandler().getConfig().getSection(method).getValue("enabled").getBoolean();
    }

    /**
     * actual backup logic
     */
    private void run_internal() {
        ConfigurationObject config = instance.getConfigHandler().getConfig();
        BackupStatus.setStatus(BackupStatusValue.STARTING);
        totalTimer.start();
        if (!locationsToBePruned.isEmpty()) {
            locationsToBePruned.clear();
        }
        int threadPriority = config.getValue("backup-thread-priority").getInt();
        Thread.currentThread().setPriority(threadPriority);
        if (initiator.isAuto()) {
            updateNextIntervalBackupTime(instance);
        }
        if (!instance.getAPIHandler().shouldStartBackup()) {
            return;
        }
        boolean backupsRequirePlayers = config.getValue("backups-require-players").getBoolean();
        if (backupsRequirePlayers && !isAutoBackupsActive() && initiator.isAuto()) {
            return;
        }
        if (!locationsToBePruned.isEmpty()) {
            locationsToBePruned.clear();
        }
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
        resetBackupBackingUp();
        try {
            instance.disableWorldAutoSave();
        } catch (InterruptedException | ExecutionException e) {
            uploadLogger.log("auto-save-disable-fail", e);
        }
        for (BackupListEntry set : backupList) {
            addOneBackupBackingUp();
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
        if (isMethodEnabled("googledrive")) {
            uploaders.add(new GoogleDriveUploader(instance, uploadLogger));
        }
        if (isMethodEnabled("onedrive")) {
            uploaders.add(new OneDriveUploader(instance, uploadLogger));
        }
        if (isMethodEnabled("dropbox")) {
            uploaders.add(new DropboxUploader(instance, uploadLogger));
        }
        if (isMethodEnabled("webdav")) {
            uploaders.add(new WebDAVUploader(instance, uploadLogger));
        }
        if (isMethodEnabled("nextcloud")) {
            uploaders.add(new NextcloudUploader(instance, uploadLogger));
        }
        if (isMethodEnabled("s3")) {
            uploaders.add(new S3Uploader(instance, uploadLogger));
        }
        if (isMethodEnabled("ftp")) {
            uploaders.add(new FTPUploader(instance, uploadLogger));
        }
        int localKeepCount = config.getValue("local-keep-count").getInt();
        if (uploaders.isEmpty() && localKeepCount == 0) {
            uploadLogger.log("backup-no-methods");
            pruneLocalBackups();
            return;
        }
        ensureMethodsAuthenticated();
        uploadBackupFiles(uploaders);
        FileUtil.deleteFolder(new File("external-backups"));
        uploadLogger.log("backup-upload-complete");
        BackupStatus.setStatus(BackupStatusValue.NOT_RUNNING);
        uploadLogger.log("upload-error-check");
        boolean errorOccurred = false;
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
            uploadLogger.broadcastRaw(getNextAutoBackup(instance));
        }
        PlayerHandler playerHandler = instance.getPlayerHandler();
        if (backupsRequirePlayers && playerHandler.getOnlinePlayers().isEmpty() && isAutoBackupsActive()) {
            uploadLogger.info("backup-disabled-inactivity");
            setAutoBackupsActive(false);
        }
        setLastBackupSuccessful(!errorOccurred);
        BackupStatus.setStatus(BackupStatusValue.PRUNING);
        pruneLocalBackups();
        totalTimer.end();
        long totalSeconds = totalTimer.getTime();
        uploadLogger.log("backup-total-time", "time", String.valueOf(totalSeconds));
        BackupStatus.setStatus(BackupStatusValue.NOT_RUNNING);
    }
    
    private void ensureMethodsAuthenticated() {
        Iterator<Uploader> iterator = uploaders.iterator();
        while (iterator.hasNext()) {
            Uploader uploader = iterator.next();
            AuthenticationProvider provider = uploader.getAuthProvider();
            if (provider != null && !Authenticator.hasRefreshToken(provider, instance)) {
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
            instance.disableWorldAutoSave();
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
                    timer.sendUploadTimeMessage(file);
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
        uploadLogger.info("external-ftp-backup-start", "socket-addr", externalBackup.getSocketAddress());
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
                ftpUploader.downloadFile(filePath, externalBackup.getTempFolderName() + "/" + backup.path + parentFolderPath);
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
            new PathBackupLocation("external-backups" + "/" + externalBackup.getTempFolderName()),
            externalBackup.format,
            true,
            new String[0]
        );
        backupList.add(backup);
        if (ftpUploader.didErrorOccur()) {
            uploadLogger.log("external-ftp-backup-failed", "socket-addr", externalBackup.getSocketAddress());
        } else {
            uploadLogger.info("external-ftp-backup-complete", "socket-addr", externalBackup.getSocketAddress());
        }
    }

    /**
     * Downloads databases from a MySQL server and stores them within the external-backups temporary folder,
     * using the specified external backup settings.
     * @param externalBackup the external backup settings
     */
    private void makeExternalDatabaseBackup(ExternalMySQLSource externalBackup) {
        uploadLogger.info("external-mysql-backup-start", "socket-addr", externalBackup.getSocketAddress());
        MySQLUploader mysqlUploader = new MySQLUploader(instance,
                externalBackup.hostname,
                externalBackup.port,
                externalBackup.username,
                externalBackup.password,
                externalBackup.ssl);
        for (ExternalDatabaseEntry database : externalBackup.databaseList) {
            for (String blacklistEntry : database.blacklist) {
                uploadLogger.log("external-mysql-backup-blacklisted", "blacklist-entry", blacklistEntry);
            }
            mysqlUploader.downloadDatabase(database.name, externalBackup.getTempFolderName(), Arrays.asList(database.blacklist));
        }
        BackupListEntry backup = new BackupListEntry(
            new PathBackupLocation("external-backups" + "/" + externalBackup.getTempFolderName()),
            externalBackup.format,
            true,
            new String[0]
        );
        backupList.add(backup);
        if (mysqlUploader.didErrorOccur()) {
            uploadLogger.log("external-mysql-backup-failed", "socket-addr", externalBackup.getSocketAddress());
        } else {
            uploadLogger.info("external-mysql-backup-complete", "socket-addr", externalBackup.getSocketAddress());
        }
    }

    /**
     * Gets the current status of the backup thread
     * @return the status of the backup thread as a {@code String}
     */
    public String getBackupStatus() {
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
        int backup = 0;
        //edge case when its in between backup steps where number is set to 0
        int backupNumber = Math.max(0, backupBackingUp-1);
        if (backupNumber <= backupList.size()) {
            backup = backupNumber;
        }
        String backupSetName = backupList.get(backup).location.toString();
        return message
            .replace("<set-name>", backupSetName)
            .replace("<set-num>", String.valueOf(backupNumber+1))
            .replace("<set-count>", String.valueOf(backupList.size()));
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
            ZonedDateTime nextBackupDate = getScheduler().getBackupDatesList().stream()
                                                    .filter(zdt -> zdt.isAfter(now))
                                                    .min(Comparator.naturalOrder())
                                                    .orElseThrow(NoSuchElementException::new);
            String dateLanguageString = instance.getConfigHandler().getConfig().getValue("advanced", "date-language").getString();
            Locale dateLanguage = Locale.forLanguageTag(dateLanguageString);
            String format = instance.getMessageHandler().getLangString("next-schedule-backup-format");
            DateTimeFormatter backupDateFormatter = DateTimeFormatter.ofPattern(format, dateLanguage);
            String message = instance.getMessageHandler().getLangString("next-schedule-backup");
            return DATE_PATTERN.matcher(message).replaceAll(nextBackupDate.format(backupDateFormatter));
        } else if (config.getValue("delay").getLong() != -1L) {
            String message = instance.getMessageHandler().getLangString("next-backup");
            long nextTime = LocalDateTime.now().until(nextIntervalBackupTime, ChronoUnit.MINUTES);
            return TIME_PATTERN.matcher(message).replaceAll(String.valueOf(nextTime));
        } else {
            return instance.getMessageHandler().getLangString("auto-backups-disabled");
        }
    }

    /**
     * Sets the time of the next interval-based backup to the current time + the configured interval.
     */
    public static void updateNextIntervalBackupTime(DriveBackupInstance instance) {
        long delay = instance.getConfigHandler().getConfig().getValue("delay").getLong();
        nextIntervalBackupTime = LocalDateTime.now().plusMinutes(delay);
    }

    @Contract (pure = true)
    public static boolean wasLastBackupSuccessful() {
        return lastBackupSuccessful;
    }
    
    private static void resetBackupBackingUp() {
        backupBackingUp = 0;
    }
    
    private static void addOneBackupBackingUp() {
        backupBackingUp++;
    }
    
}

package ratismal.drivebackup.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.uploaders.UploadLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class FileUtil {
    private static final String NAME_KEYWORD = "%NAME";
    private static final Pattern NAME = Pattern.compile(NAME_KEYWORD, Pattern.LITERAL);
    
    private final UploadLogger logger;
    private final DriveBackupInstance instance;

    @Contract (pure = true)
    public FileUtil(DriveBackupInstance instance, UploadLogger logger) {
        this.logger = logger;
        this.instance = instance;
    }

    /**
     * Gets the local backups in the specified folder as a {@code TreeMap} with their creation date and a reference to them.
     * @param location the location of the folder containing the backups
     * @param formatter the format of the file name
     * @return The list of backups
     */
    public @NotNull TreeMap<Long, File> getLocalBackups(String location, LocalDateTimeFormatter formatter) {
        location = escapeBackupLocation(location);
        TreeMap<Long, File> backupList = new TreeMap<>();
        String localDir = instance.getConfigHandler().getConfig().getValue("local-save-directory").getString();
        String path = new File(localDir).getAbsolutePath() + File.separator + location;
        File[] files = new File(path).listFiles();
        if (files == null) {
            return backupList;
        }
        for (File file : files) {
            if (file.getName().endsWith(".zip")) {
                long dateOfFile = TimeUnit.MILLISECONDS.toSeconds(file.lastModified());
                backupList.put(dateOfFile, file);
            }
        }
        return backupList;
    }

    /**
     * Creates a local backup zip file for the specified file/folder.
     * @param location the location of the file or folder
     * @param formatter the format of the file name
     * @param blacklistGlobs a list of glob patterns of files/folders to not include in the backup.
     * @throws Exception
     */
    public void makeBackup(@NotNull String location, LocalDateTimeFormatter formatter, List<String> blacklistGlobs) throws Exception {
        if (location.charAt(0) == '/') {
            throw new IllegalArgumentException("Location cannot start with a slash");
        }
        ZoneOffset zoneOffset = ZoneOffset.of(instance.getConfigHandler().getConfig().getValue("advanced", "date-timezone").getString());
        ZonedDateTime now = ZonedDateTime.now(zoneOffset);
        String fileName = formatter.format(now);
        String subFolderName = location;
        if (isBaseFolder(subFolderName)) {
            subFolderName = "root";
        }
        String localDir = instance.getConfigHandler().getConfig().getValue("local-save-directory").getString();
        File path = new File(escapeBackupLocation(localDir + "/" + subFolderName));
        if (!path.exists()) {
            path.mkdirs();
        }
        List<BlacklistEntry> blacklist = new ArrayList<>();
        for (String blacklistGlob : blacklistGlobs) {
            BlacklistEntry blacklistEntry = new BlacklistEntry(
                blacklistGlob, 
                FileSystems.getDefault().getPathMatcher("glob:" + blacklistGlob)
                );
            blacklist.add(blacklistEntry);
        }
        BackupFileList fileList = generateFileList(location, blacklist);
        for (BlacklistEntry blacklistEntry : fileList.getBlacklist()) {
            String globPattern = blacklistEntry.getGlobPattern();
            int blacklistedFiles = blacklistEntry.getBlacklistedFiles();
            if (blacklistedFiles > 0) {
                Map<String, String> placeholders = new HashMap<>(2);
                placeholders.put("blacklisted-files-count", String.valueOf(blacklistedFiles));
                placeholders.put("glob-pattern", globPattern);
                logger.info("local-backup-backlisted", placeholders);
            }
        }
        int filesInBackupFolder = fileList.getFilesInBackupFolder();
        if (filesInBackupFolder > 0) {
            logger.info("local-backup-in-backup-folder", "files-in-backup-folder-count", String.valueOf(filesInBackupFolder));
        }
        if (fileName.contains(NAME_KEYWORD)) {
            int lastSeparatorIndex = Math.max(location.lastIndexOf('/'), location.lastIndexOf('\\'));
            String lastFolderName = location.substring(lastSeparatorIndex + 1);
            fileName = NAME.matcher(fileName).replaceAll(Matcher.quoteReplacement(lastFolderName));
        }
        zipIt(location, path.getPath() + "/" + fileName, fileList);
    }

    /**
     * Deletes the oldest files in the specified folder past the number to retain locally.
     * <p>
     * The number of files to retain locally is specified by the user in the {@code config.yml}
     * @param location the location of the folder containing the backups
     * @param formatter the format of the file name
     */
    public void pruneLocalBackups(String location, LocalDateTimeFormatter formatter) {
        location = escapeBackupLocation(location);
        if (isBaseFolder(location)) {
            location = "root";
        }
        logger.info("local-backup-pruning-start", "location", location);
        int localKeepCount = instance.getConfigHandler().getConfig().getValue("local-keep-count").getInt();
        if (localKeepCount != -1) {
            try {
                TreeMap<Long, File> backupList = getLocalBackups(location, formatter);
                String size = String.valueOf(backupList.size());
                String keepCount = String.valueOf(localKeepCount);
                Map<String, String> placeholders = new HashMap<>(2);
                placeholders.put("backup-count", size);
                placeholders.put("backup-limit", keepCount);
                if (backupList.size() > localKeepCount) {
                    logger.info("local-backup-limit-reached", placeholders);
                } else {
                    logger.info("local-backup-limit-not-reached", placeholders);
                    return;
                }
                while (backupList.size() > localKeepCount) {
                    File fileToDelete = backupList.descendingMap().lastEntry().getValue();
                    long dateOfFile = backupList.descendingMap().lastKey();
                    if (!fileToDelete.delete()) {
                        logger.log("local-backup-file-failed-to-delete", "local-backup-name", fileToDelete.getName());
                    } else {
                        logger.info("local-backup-file-deleted", "local-backup-name", fileToDelete.getName());
                    }
                    backupList.remove(dateOfFile);
                }
                logger.log("local-backup-pruning-complete", "location", location);
            } catch (Exception e) {
                logger.log("local-backup-failed-to-delete");
                MessageUtil.sendConsoleException(e);
            }
        } else {
            logger.info("local-backup-no-limit");
        }
    }

    /**
     * Zips files in the specified folder into the specified file location.
     * @param inputFolderPath the path of the zip file to create
     * @param outputFilePath the path of the folder to put it in
     * @param fileList file to include in the zip
     */
    private void zipIt(String inputFolderPath, String outputFilePath, BackupFileList fileList) throws Exception {
        byte[] buffer = new byte[1024];
        FileOutputStream fileOutputStream;
        ZipOutputStream zipOutputStream = null;
        String formattedInputFolderPath = new File(inputFolderPath).getName();
        if (isBaseFolder(inputFolderPath)) {
            formattedInputFolderPath = "root";
        }
        try {
            fileOutputStream = new FileOutputStream(outputFilePath);
            int compression = instance.getConfigHandler().getConfig().getValue("zip-compression").getInt();
            if (compression > 9) {
                compression = 9;
            }
            if (compression < 0) {
                compression = 0;
            }
            zipOutputStream = new ZipOutputStream(fileOutputStream);
            zipOutputStream.setLevel(compression);
            for (String file : fileList.getList()) {
                ZipEntry entry = new ZipEntry(formattedInputFolderPath + "/" + file);
                String filePath = inputFolderPath + "/" + file;
                BasicFileAttributes fileAttributes = null;
                try {
                    fileAttributes = Files.readAttributes(Paths.get(filePath), BasicFileAttributes.class);
                } catch(Exception ignored) { }
                if (fileAttributes == null) {
                    logger.info("local-backup-failed-attributes", "file-path", filePath);
                } else {
                    entry.setCreationTime(fileAttributes.creationTime());
                    entry.setLastAccessTime(fileAttributes.lastAccessTime());
                    entry.setLastModifiedTime(fileAttributes.lastModifiedTime());
                    entry.setSize(fileAttributes.size());
                }
                zipOutputStream.putNextEntry(entry);
                try (FileInputStream fileInputStream = new FileInputStream(filePath)){
                    int len;
                    while ((len = fileInputStream.read(buffer)) > 0) {
                        zipOutputStream.write(buffer, 0, len);
                    }
                } catch (Exception e) {
                    // Don't send warning for .lock files, they will always be locked.
                    if (!filePath.endsWith(".lock")) {
                        logger.info("local-backup-failed-to-include", "file-path", filePath);
                    }
                }
                zipOutputStream.closeEntry();
            }
            zipOutputStream.close();
        } catch (Exception exception) {
            if (zipOutputStream != null) {
                zipOutputStream.close();
            }
            throw exception; 
        }
    }

    /**
     * A list of files to put in a zip file
     * Mutable.
     */
    private static class BackupFileList {
        private int filesInBackupFolder;
        private List<String> fileList;
        private List<BlacklistEntry> blacklist;
        
        @Contract (pure = true)
        private BackupFileList(List<BlacklistEntry> blacklist) {
            filesInBackupFolder = 0;
            fileList = new ArrayList<>();
            this.blacklist = blacklist;
        }

        void incFilesInBackupFolder() {
            filesInBackupFolder++;
        }

        int getFilesInBackupFolder() {
            return filesInBackupFolder;
        }

        void appendToList(String file) {
            fileList.add(file);
        }

        List<String> getList() {
            return fileList;
        }

        List<BlacklistEntry> getBlacklist() {
            return blacklist;
        }
    }

    /**
     * Generates a list of files to put in the zip created from the specified folder.
     * @param inputFolderPath The path of the folder to create the zip from
     * @throws Exception
     */
    @NotNull
    private BackupFileList generateFileList(String inputFolderPath, List<BlacklistEntry> blacklist) throws Exception {
        BackupFileList fileList = new BackupFileList(blacklist);
        generateFileList(new File(inputFolderPath), inputFolderPath, fileList);
        return fileList;
    }

    /**
     * Adds the specified file or folder to the list of files to put in the zip created from the specified folder.
     * @param file the file or folder to add
     * @param inputFolderPath the path of the folder to create the zip 
     * @param fileList the list of files to add the specified file or folder to.
     * @throws Exception
     */
    private void generateFileList(@NotNull File file, String inputFolderPath, BackupFileList fileList) throws Exception {
        BasicFileAttributes fileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
        if (fileAttributes.isRegularFile()) {
            // Verify not backing up previous backups
            if (file.getCanonicalPath().startsWith(new File(ConfigParser.getConfig().backupStorage.localDirectory).getCanonicalPath())) {
                fileList.incFilesInBackupFolder();
                return;
            }
            Path relativePath = Paths.get(inputFolderPath).relativize(file.toPath());
            for (BlacklistEntry blacklistEntry : fileList.getBlacklist()) {
                if (blacklistEntry.getPathMatcher().matches(relativePath)) {
                    blacklistEntry.incBlacklistedFiles();
                    return;
                }
            }
            fileList.appendToList(relativePath.toString());
        } else if (fileAttributes.isDirectory()) {
            String[] files = file.list();
            if (files == null) {
                return;
            }
            for (String filename : files) {
                generateFileList(new File(file, filename), inputFolderPath, fileList);
            }
        } else {
            logger.info("local-backup-failed-to-include", "file-path", file.getAbsolutePath());
        }
    }

    /**
     * Removes ".." from the location string to keep the location's backup folder within the local-save-directory.
     * @param location the unescaped location
     * @return the escaped location
     */
    @NotNull
    @Contract (pure = true)
    private static String escapeBackupLocation(@NotNull String location) {
        return location.replace("../", "");
    }

    /**
     * Finds all folders that match a glob
     * @param glob the glob to search
     * @param rootPath the path to start searching from
     * @return List of all folders that match this glob under rootPath
     */
    public static List<Path> generateGlobFolderList(String glob, String rootPath) {
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:./" + glob);
        List<Path> list = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(Paths.get(rootPath))) {
            list = walk.filter(pathMatcher::matches).filter(Files::isDirectory).collect(Collectors.toList());
        } catch (IOException exception) {
            return list;
        }
        return list;
    }

    /**
     * Whether the specified folder is the base folder of the Minecraft server.
     * <p>
     * In other words, whether the folder is the folder containing the server jar.
     * @param folderPath the path of the folder
     * @return whether the folder is the base folder
     */
    public static boolean isBaseFolder(String folderPath) {
        return new File(folderPath).getPath().equals(".");
    }

    /**
     * Deletes the specified folder
     * @param folder the folder to be deleted
     * @return whether deleting the folder was successful
     */
    public static boolean deleteFolder(@NotNull File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteFolder(file);
            }
        }
        return folder.delete();
    }
}

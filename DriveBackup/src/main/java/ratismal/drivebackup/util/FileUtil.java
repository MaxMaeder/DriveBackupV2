package ratismal.drivebackup.util;

import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class FileUtil {
    private static final TreeMap<Long, File> backupList = new TreeMap<>();
    private static final List<String> fileList = new ArrayList<>();
    private static List<BlacklistEntry> blacklist = new ArrayList<>();
    private static int backupFiles = 0;

    /**
     * Gets the most recent backup of the specified backup type
     * @param type the type of back up (world, plugin, etc)
     * @param formatter the format of the file name
     * @return The file to upload
     */
    public static File getNewestBackup(String type, LocalDateTimeFormatter formatter) {
        type = type.replace("../", "");

        backupList.clear();
        String path = new File(ConfigParser.getConfig().backupStorage.localDirectory).getAbsolutePath() + "/" + type;
        File[] files = new File(path).listFiles();

        for (File file : files) {
            if (file.getName().endsWith(".zip")) {

                String dateString = file.getName();

                try {
                    ZonedDateTime date = formatter.parse(dateString);
                    backupList.put(date.toEpochSecond(), file);
                } catch (Exception e) {
                    backupList.put(0L, file);
                    MessageUtil.Builder().text("Unable to parse date format of stored backup \"" + dateString + "\", this can be due to the date format being updated in the config.yml").send();
                    MessageUtil.Builder().text("Backup will be deleted first").send();
                }
            }
        }

        return backupList.descendingMap().firstEntry().getValue();
    }

    /**
     * Creates a local backup zip file for the specified backup type
     * @param type what to back up (world, plugin, etc)
     * @param formatter the format of the file name
     * @param blacklistGlobs a list of glob patterns of files/folders to not include in the backup
     * @throws Exception
     */
    public static void makeBackup(String type, LocalDateTimeFormatter formatter, List<String> blacklistGlobs) throws Exception {
        Config config = ConfigParser.getConfig();

        if (type.charAt(0) == '/') {
            throw new IllegalArgumentException(); 
        }

        fileList.clear();

        ZonedDateTime now = ZonedDateTime.now(config.advanced.dateTimezone);
        String fileName = formatter.format(now);

        blacklist.clear();
        backupFiles = 0;
        for (String blacklistGlob : blacklistGlobs) {
            BlacklistEntry blacklistEntry = new BlacklistEntry(
                blacklistGlob, 
                FileSystems.getDefault().getPathMatcher("glob:" + blacklistGlob)
                );

            blacklist.add(blacklistEntry);
        }

        String subfolderName = type;
        if (isBaseFolder(subfolderName)) {
            subfolderName = "root";
        }

        File path = new File((config.backupStorage.localDirectory + "/" + subfolderName).replace("../", "")); // Keeps working directory inside backups folder
        if (!path.exists()) {
            path.mkdirs();
        }

        generateFileList(type);

        for (BlacklistEntry blacklistEntry : blacklist) {
            String globPattern = blacklistEntry.getGlobPattern();
            int blacklistedFiles = blacklistEntry.getBlacklistedFiles();

            if (blacklistedFiles > 0) {
                MessageUtil.Builder().text("Didn't include " + blacklistedFiles + " file(s) in the backup, as they are blacklisted by \"" + globPattern + "\"").toConsole(true).send();
            }
        }

        if (backupFiles > 0) {
            MessageUtil.Builder().text("Didn't include " + backupFiles + " file(s) in the backup, as they are in the folder used for backups").toConsole(true).send();
        }

        zipIt(type, path.getPath() + "/" + fileName);
    }

    /**
     * Deletes the oldest files in the specified folder past the number to retain locally
     * <p>
     * The number of files to retain locally is specified by the user in the {@code config.yml}
     * @param type the type of file (ex. plugins, world)
     * @param formatter the format of the files name
     * @throws IOException
     */
    public static void deleteFiles(String type, LocalDateTimeFormatter formatter) throws IOException {
        int localKeepCount = ConfigParser.getConfig().backupStorage.localKeepCount;
        type = type.replace("../", "");

        if (localKeepCount != -1) {
            try {
                getNewestBackup(type, formatter);

                if (backupList.size() > localKeepCount) {
                    MessageUtil.Builder().text("There are " + backupList.size() + " file(s) which exceeds the local limit of " + localKeepCount + ", deleting oldest").toConsole(true).send();
                }
                

                while (backupList.size() > localKeepCount) {
                    File fileToDelete = backupList.descendingMap().lastEntry().getValue();
                    long dateOfFile = backupList.descendingMap().lastKey();

                    if (!fileToDelete.delete()) {
                        MessageUtil.Builder().text("Failed to delete local backup \"" + fileToDelete.getName() + "\"").toConsole(true).send();
                    }
                    
                    backupList.remove(dateOfFile);
                }
            } catch (Exception e) {
                MessageUtil.sendConsoleException(e);
                MessageUtil.Builder().text("Local backup deletion failed").toConsole(true).send();
            }
        }
    }

    /**
     * Zips files in the specified folder into the specified file location
     * @param inputFolderPath the path of the zip file to create
     * @param outputFilePath the path of the folder to put it in
     */
    private static void zipIt(String inputFolderPath, String outputFilePath) throws Exception {
        byte[] buffer = new byte[1024];
        FileOutputStream fileOutputStream;
        ZipOutputStream zipOutputStream = null;

        String formattedInputFolderPath = new File(inputFolderPath).getName();
        if (isBaseFolder(inputFolderPath)) {
            formattedInputFolderPath = "root";
        }

        try {
            fileOutputStream = new FileOutputStream(outputFilePath);
            zipOutputStream = new ZipOutputStream(fileOutputStream);
            zipOutputStream.setLevel(ConfigParser.getConfig().backupStorage.zipCompression);

            for (String file : fileList) {
                zipOutputStream.putNextEntry(new ZipEntry(formattedInputFolderPath + "/" + file));

                try (FileInputStream fileInputStream = new FileInputStream(inputFolderPath + "/" + file)) {
                    
                    int len;
                    while ((len = fileInputStream.read(buffer)) > 0) {
                        zipOutputStream.write(buffer, 0, len);
                    }
                } catch (Exception e) {
                    String filePath = new File(inputFolderPath, file).getPath();

                    if (!filePath.endsWith(".lock")) { // Don't send warning for .lock files, they will always be locked
                        MessageUtil.Builder().text("Failed to include \"" + filePath + "\" in the backup, is it locked?").toConsole(true).send();
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
     * Generates a list of files to put in the zip created from the specified folder
     * @param inputFolderPath The path of the folder to create the zip from
     * @throws Exception
     */
    private static void generateFileList(String inputFolderPath) throws Exception {
        generateFileList(new File(inputFolderPath), inputFolderPath);
    }

    /**
     * Adds the specified file or folder to the generated list of files to put in the zip created from the specified folder
     * @param file the file or folder to add
     * @param inputFolderPath the path of the folder to create the zip from
     * @throws Exception
     */
    private static void generateFileList(File file, String inputFolderPath) throws Exception {

        if (file.isFile()) {
            // Verify not backing up previous backups
            if (file.getCanonicalPath().startsWith(new File(ConfigParser.getConfig().backupStorage.localDirectory).getCanonicalPath())) {
                backupFiles++;

                return;
            }

            Path relativePath = Paths.get(inputFolderPath).relativize(file.toPath());

            for (BlacklistEntry blacklistEntry : blacklist) {
                if (blacklistEntry.getPathMatcher().matches(relativePath)) {
                    blacklistEntry.incrementBlacklistedFiles();

                    return;
                }
            }
            fileList.add(relativePath.toString());
        }

        if (file.isDirectory()) {
            for (String filename : file.list()) {
                generateFileList(new File(file, filename), inputFolderPath);
            }
        }
    }

    /**
     * Finds all folders that match a glob
     * @param glob the glob to search
     * @param rootPath the path to start searching from
     * @return List of all folders that match this glob under rootPath
     */
    public static List<Path> generateGlobFolderList(String glob, String rootPath) {
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:./" + glob);
        List<Path> list = new ArrayList<Path>();
        try (Stream<Path> walk = Files.walk(Paths.get(rootPath))) {
            list = walk.filter(pathMatcher::matches).filter(Files::isDirectory).collect(Collectors.toList());
        } catch (IOException exception) {
            return list;
        }
        return list;
    }

    /**
     * Whether the specified folder is the base folder of the Minecraft server
     * <p>
     * In other words, whether the folder is the folder containing the server jar
     * @param folderPath the path of the folder
     * @return whether the folder is the base folder
     * @throws Exception
     */
    public static boolean isBaseFolder(String folderPath) throws Exception {
        return new File(folderPath).getPath().equals(".");
    }

    /**
     * Deletes the specified folder
     * @param folder the folder to be deleted
     * @return whether deleting the folder was successful
     */
    public static boolean deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteFolder(file);
            }
        }
        return folder.delete();
    }
}

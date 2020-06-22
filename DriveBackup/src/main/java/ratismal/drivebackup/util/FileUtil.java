package ratismal.drivebackup.util;

import ratismal.drivebackup.config.Config;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class FileUtil {

    private static final TreeMap<Date, File> backupList = new TreeMap<>();
    private static final List<String> fileList = new ArrayList<>();
    private static List<String> blackList;

    /**
     * Gets the file to upload
     *
     * @param type   What we're backing up (world, plugin, etc)
     * @param format Format of the file name
     * @param output Should we upload the available files to console?
     * @return The file to upload
     */
    public static File getFileToUpload(String type, String format, boolean output) {
        type = type.replace(".." + File.separator, "");

        backupList.clear();
        String path = new File(Config.getDir()).getAbsolutePath() + File.separator + type;
        File[] files = new File(path).listFiles();
        subFiles(format, files);
        if (output) {
            for (Map.Entry<Date, File> entry : backupList.descendingMap().entrySet()) {
                MessageUtil.sendConsoleMessage(entry.getValue().getName() + " - " + entry.getKey());
            }
            MessageUtil.sendConsoleMessage("The most recent is " + backupList.descendingMap().firstEntry().getValue());
        }
        return backupList.descendingMap().firstEntry().getValue();
    }

    /**
     * Gets a list of backups
     *
     * @param formatString Format of the file name
     * @param files        A list of files
     */
    private static void subFiles(String formatString, File[] files) {
        for (File file : files) {
            if (file.isDirectory()) {
                subFiles(formatString, file.listFiles()); // Calls same method again.
            } else {
                if (file.getName().endsWith(".zip")) {
                    String dateString = file.getName();
                    DateFormat format = new SimpleDateFormat(formatString, new Locale(Config.getDateLanguage()));
                    try {
                        Date date = format.parse(dateString);
                        backupList.put(date, file);
                    } catch (Exception e) {
                        MessageUtil.sendConsoleException(e);
                    }
                }
            }
        }
    }


    /**
     * Creates a backup
     *
     * @param type         What we're backing up (world, plugin, etc)
     * @param formatString Format of the file name
     */
    public static void makeBackup(String type, String formatString, List<String> _blackList) throws IOException {
        if (type.charAt(0) == File.separatorChar) {
            throw new IOException(); 
        }

        try {
            fileList.clear();
            DateFormat format = new SimpleDateFormat(formatString, new Locale(Config.getDateLanguage()));
            String fileName = format.format(new Date());
            blackList = _blackList;

            File path = new File(new String(Config.getDir() + File.separator + type).replace(".." + File.separator, "")); // Keeps working directory inside backups folder
            if (!path.exists()) {
                path.mkdirs();
            }

            generateFileList(new File(type), type);
            zipIt(new String(Config.getDir() + File.separator + type + File.separator + fileName).replace(".." + File.separator, ""), type);


        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            MessageUtil.sendConsoleMessage("Backup creation failed.");
        }
    }

    /**
     * Deletes the oldest files in the specified folder past the number to retain locally
     * <p>
     * The number of files to retain locally is specified by the user in the {@code config.yml}
     * @param type the type of file (ex. plugins, world)
     * @param formatString the format of the files name
     * @throws IOException
     */
    public static void deleteFiles(String type, String formatString) throws IOException {
        if (Config.getLocalKeepCount() != -1) {
            try {
                getFileToUpload(type, formatString, false);

                if (backupList.size() > Config.getLocalKeepCount()) {
                    MessageUtil.sendConsoleMessage("There are " + backupList.size() + " file(s) which exceeds the local limit of " + Config.getLocalKeepCount() + ", deleting");
                }
                

                while (backupList.size() > Config.getLocalKeepCount()) {
                    File fileToDelete = backupList.descendingMap().lastEntry().getValue();
                    Date dateOfFile = backupList.descendingMap().lastKey();

                    if (fileToDelete.delete()) {
                        MessageUtil.sendConsoleMessage("Old local backup deleted");
                    } else {
                        MessageUtil.sendConsoleMessage("Failed to delete local backup " + backupList.descendingMap().lastEntry().getValue().getName());
                    }
                    
                    backupList.remove(dateOfFile);
                }
            } catch (Exception e) {
                MessageUtil.sendConsoleException(e);
                MessageUtil.sendConsoleMessage("Local backup deletion failed");
            }
        }
    }

    /**
     * Zips files into a zip file
     *
     * @param zipFile      The name of the zip file
     * @param sourceFolder The name of the folder to put it in
     */
    private static void zipIt(String zipFile, String sourceFolder) {
        byte[] buffer = new byte[1024];
        String source;
        FileOutputStream fos;
        ZipOutputStream zos = null;

        try {
            try {
                source = sourceFolder.substring(sourceFolder.lastIndexOf(File.separator) + 1, sourceFolder.length());
            } catch (Exception e) {
                source = sourceFolder;
            }

            fos = new FileOutputStream(zipFile);
            zos = new ZipOutputStream(fos);

            for (String file : fileList) {
                ZipEntry ze = new ZipEntry(source + File.separator + file);
                zos.putNextEntry(ze);
                try (FileInputStream in = new FileInputStream(sourceFolder + File.separator + file)) {
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                } catch (Exception e) {
                    MessageUtil.sendConsoleException(e);
                }
            }
            zos.closeEntry();
        } catch (Exception ex) {
            MessageUtil.sendConsoleException(ex);
        } finally {
            try {
                if (zos != null) {
                    zos.close();
                }
            } catch (Exception e) {
                MessageUtil.sendConsoleException(e);
            }
        }
    }

    /**
     * Gets a list of files to put in the zip
     *
     * @param node         File or folder to add
     * @param sourceFolder Folder that we are looking in
     */
    private static void generateFileList(File node, String sourceFolder) {

        // add file only
        if (node.isFile()) {
            if (!blackList.contains(node.getName())) {
                fileList.add(generateZipEntry(node.toString(), sourceFolder));
            }
        }

        if (node.isDirectory()) {
            String[] subNote = node.list();
            for (String filename : subNote) {
                if (!blackList.contains(node.getName())) {
                    generateFileList(new File(node, filename), sourceFolder);
                }
            }
        }
    }

    /**
     * Gets the name of the file to put into the zip
     *
     * @param file         File name
     * @param sourceFolder Folder name
     * @return New name of the file
     */
    private static String generateZipEntry(String file, String sourceFolder) {
        return file.substring(sourceFolder.length() + 1, file.length());
    }

    /**
     * Returns a list of the paths of the ZIP files and their modification dates inside the local specified folder
     * @param folderPath the path of the folder
     * @return the list of files
     */
    public static HashMap<String, Date> getZipFiles(String folderPath) {
        HashMap<String, Date> filePaths = new HashMap<>();

        for (File file : new File(folderPath).listFiles()) {
            if (file.getName().endsWith(".zip")) {
                filePaths.put(
                        folderPath + file.getName(), 
                        new Date(file.lastModified()));
            }
        }

        return filePaths;
    }

    /**
     * Copies files from one folder to another
     * @param sourceFolderPath the folder to the copy files from
     * @param destinationFolderPath the folder to copy the files to
     * @throws Exception
     */
    public static void copyFolder(String sourceFolderPath, String destinationFolderPath) throws IOException {
        File sourceFolder = new File(sourceFolderPath);
        File destinationFolder = new File(destinationFolderPath);

        if (sourceFolder.isDirectory()) {
            if (!destinationFolder.exists()) {
                destinationFolder.mkdirs();
            }
            
            String[] childFiles = sourceFolder.list();
            for (String childFile : childFiles) {
                copyFolder(sourceFolderPath + File.separator + childFile, destinationFolderPath + File.separator + childFile);
            }
        } else {
            InputStream inputStream = new FileInputStream(sourceFolder);
            OutputStream outputStream = new FileOutputStream(destinationFolder);
            
            byte[] buffer = new byte[1024];
            int length;

            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            inputStream.close();
            outputStream.close();
        }
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

    /**
     * Extracts the specified zip file into the specified folder
     * @param zipFilePath the path to the zip file
     * @param destinationFolderPath the path to the folder to extract the zip file into
     * @throws IOException
     */
    public static void extractFolder(String zipFilePath, String destinationFolderPath) throws IOException {
        File destinationFolder = new File(destinationFolderPath);
        if (!destinationFolder.exists()) {
            destinationFolder.mkdir();
        }

        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry zipEntry = zipInputStream.getNextEntry();

        while (zipEntry != null) {
            String filePath = destinationFolderPath + File.separator + zipEntry.getName();

            if (!zipEntry.isDirectory()) {
                extractFile(zipInputStream, filePath);
            } else {
                File clildFolder = new File(filePath);
                clildFolder.mkdir();
            }

            zipInputStream.closeEntry();
            zipEntry = zipInputStream.getNextEntry();
        }

        zipInputStream.close();
    }

    /**
     * Extracts the specified zip input stream into the specified folder
     * @param zipInputStream the zip input stream
     * @param filePath the path to the folder to extract the zip file into
     * @throws IOException
     */
    private static void extractFile(ZipInputStream zipInputStream, String filePath) throws IOException {
        BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(filePath));
        byte[] buffer = new byte[4096];

        int read = 0;
        while ((read = zipInputStream.read(buffer)) != -1) {
            bufferedOutputStream.write(buffer, 0, read);
        }

        bufferedOutputStream.close();
    }
}


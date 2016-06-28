package ratismal.drivebackup.util;

import ratismal.drivebackup.config.Config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
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
        backupList.clear();
        String path = new File(Config.getDir()).getAbsolutePath() + "/" + type;
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
                    DateFormat format = new SimpleDateFormat(formatString, Locale.ENGLISH);
                    try {
                        Date date = format.parse(dateString);
                        backupList.put(date, file);
                    } catch (Exception e) {
                        e.printStackTrace();
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
    public static void makeBackup(String type, String formatString, List<String> _blackList) {
        try {
            fileList.clear();
            DateFormat format = new SimpleDateFormat(formatString, Locale.ENGLISH);
            String fileName = format.format(new Date());
            File path = new File(Config.getDir());
            blackList = _blackList;
            if (!path.exists()) {
                path.mkdir();
            }
            path = new File(Config.getDir() + "/" + type);
            if (!path.exists()) {
                path.mkdir();
            }

            generateFileList(new File(type), type);
            zipIt(Config.getDir() + "/" + type + "/" + fileName, type);


        } catch (Exception e) {
            e.printStackTrace();
            MessageUtil.sendConsoleMessage("Backup creation failed.");
        }

        if (Config.getKeepCount() != -1) {
            try {
                getFileToUpload(type, formatString, false);
                while (backupList.size() > Config.getKeepCount()) {
                    File fileToDelete = backupList.descendingMap().lastEntry().getValue();
                    Date dateOfFile = backupList.descendingMap().lastKey();
                    if (fileToDelete.delete()) {
                        MessageUtil.sendConsoleMessage("Old backup deleted.");
                    } else {
                        MessageUtil.sendConsoleMessage("Failed to delete backup " + backupList.descendingMap().lastEntry().getValue().getName());
                    }
                    backupList.remove(dateOfFile);
                }
            } catch (Exception e) {
                if (Config.isDebug())
                    e.printStackTrace();
                MessageUtil.sendConsoleMessage("Backup deletion failed.");
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
        // System.out.println("Making new zip " + zipFile);
        byte[] buffer = new byte[1024];
        String source;
        FileOutputStream fos;
        ZipOutputStream zos = null;

        try {
            try {
                source = sourceFolder.substring(sourceFolder.lastIndexOf("/") + 1, sourceFolder.length());
            } catch (Exception e) {
                source = sourceFolder;
            }
            fos = new FileOutputStream(zipFile);
            zos = new ZipOutputStream(fos);

            //  MessageUtil.sendConsoleMessage("Output to Zip : " + zipFile);

            for (String file : fileList) {
                ZipEntry ze = new ZipEntry(source + File.separator + file);
                zos.putNextEntry(ze);
                try (FileInputStream in = new FileInputStream(sourceFolder + File.separator + file)) {
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                }
            }
            zos.closeEntry();
            // MessageUtil.sendConsoleMessage("Folder successfully compressed");
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                if (zos != null) {
                    zos.close();
                }
            } catch (IOException e) {
                if (Config.isDebug())
                    e.printStackTrace();
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
            if (!blackList.contains(node.getName()))
                fileList.add(generateZipEntry(node.toString(), sourceFolder));
        }

        if (node.isDirectory()) {
            String[] subNote = node.list();
            for (String filename : subNote) {
                if (!blackList.contains(node.getName()))
                    generateFileList(new File(node, filename), sourceFolder);
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
}


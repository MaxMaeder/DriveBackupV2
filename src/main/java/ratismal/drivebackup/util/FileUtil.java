package ratismal.drivebackup.util;

import org.bukkit.command.CommandSender;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.config.Config;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class FileUtil {

    public static TreeMap<Date, File> backupList = new TreeMap<>();
    public static List<String> fileList = new ArrayList<String>();

    public static File getFileToUpload(String type, String format, boolean output) {
        backupList.clear();
        String path = new File(Config.getDir()).getAbsolutePath() + "/" + type;
        //MessageUtil.sendConsoleMessage("Searching for backups in " + path);
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

    public static void subFiles(String formatString, File[] files) {
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

    public static void makeBackup(String type, String formatString) {
        try {
            fileList.clear();
            DateFormat format = new SimpleDateFormat(formatString, Locale.ENGLISH);
            String fileName = format.format(new Date());
            File path = new File(Config.getDir());
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
                e.printStackTrace();
                MessageUtil.sendConsoleMessage("Backup deletion failed.");
            }
        }

    }



    public static void zipIt(String zipFile, String sourceFolder) {
        byte[] buffer = new byte[1024];
        String source = "";
        FileOutputStream fos = null;
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
            FileInputStream in = null;

            for (String file : fileList) {
                ZipEntry ze = new ZipEntry(source + File.separator + file);
                zos.putNextEntry(ze);
                try {
                    in = new FileInputStream(sourceFolder + File.separator + file);
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                } finally {
                    in.close();
                }
            }
            zos.closeEntry();
           // MessageUtil.sendConsoleMessage("Folder successfully compressed");
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            try {
                zos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void generateFileList(File node, String sourceFolder) {

        // add file only
        if (node.isFile()) {
            fileList.add(generateZipEntry(node.toString(), sourceFolder));
        }

        if (node.isDirectory()) {
            String[] subNote = node.list();
            for (String filename : subNote) {
                generateFileList(new File(node, filename), sourceFolder);
            }
        }
    }

    private static String generateZipEntry(String file, String sourceFolder) {
        return file.substring(sourceFolder.length() + 1, file.length());
    }
}


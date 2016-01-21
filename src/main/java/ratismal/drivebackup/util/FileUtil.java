package ratismal.drivebackup.util;

import org.bukkit.command.CommandSender;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.config.Config;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class FileUtil {

    public static TreeMap<Date, File> backupList = new TreeMap<>();
   // public static List<File> backupList = new ArrayList<>();

    public static File getFileToUpload(CommandSender sender, boolean output) {
        backupList.clear();
        String path = new File(Config.getDir()).getAbsolutePath();
        MessageUtil.sendMessage(sender, "Searching for backups in " + path);
        File[] files = new File(path).listFiles();
        subFiles(files);
        if (output) {
            for (Map.Entry<Date, File> entry : backupList.descendingMap().entrySet()) {
                MessageUtil.sendMessage(sender, entry.getValue().getName() + " - " + entry.getKey());
            }
            MessageUtil.sendMessage(sender, "The most recent is " + backupList.descendingMap().firstEntry().getValue());
            /*TreeMap<Date, File> sortedMap = new TreeMap<Date, File>(backupList);
            for (Date date : sortedMap.descendingMap().keySet()) {
                MessageUtil.sendMessage(sender, date.toString());
            }
            MessageUtil.sendMessage(sender, "The most recent is " + sortedMap.descendingMap().firstEntry().getValue());
            */
        }
        return backupList.descendingMap().firstEntry().getValue();
    }

    public static void subFiles(File[] files) {
        for (File file : files) {
            if (file.isDirectory()) {
                //System.out.println("Directory: " + file.getName());
                subFiles(file.listFiles()); // Calls same method again.
            } else {
                //System.out.println("File: " + file.getName());
                if (file.getName().endsWith(".zip")) {
                    String dateString = file.getName();
                    String formatString = Config.getFormat();
                    // "Backup-world--%M-%D--%h-%s.zip"
                    DateFormat format = new SimpleDateFormat(formatString, Locale.ENGLISH);
                    try {
                        Date date = format.parse(dateString);
                        //System.out.println(date);
                        backupList.put(date, file);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}

package ratismal.drivebackup;

import org.bukkit.command.CommandSender;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.googledrive.GoogleUploader;
import ratismal.drivebackup.util.FileUtil;
import ratismal.drivebackup.util.MessageUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Ratismal on 2016-01-22.
 */

public class UploadThread implements Runnable {


    public UploadThread() {
    }

    @Override
    public void run() {
        MessageUtil.sendMessageToAllPlayers("Creating backups, server may lag for a little while...");

        // Create Backup Here

        HashMap<String, HashMap<String, String>> backupList = Config.getBackupList();
        for (Map.Entry<String, HashMap<String, String>> set : backupList.entrySet()) {
            String type = set.getKey();
            String format = set.getValue().get("format");
            String create = set.getValue().get("create");

            if (create.equalsIgnoreCase("true")) {
                FileUtil.makeBackup(type, format);
            }

            File file = FileUtil.getFileToUpload(type, format, false);

            try {
                MessageUtil.sendConsoleMessage("Uploading file to GoogleDrive");
                GoogleUploader.uploadFile(file, false, type);
                MessageUtil.sendConsoleMessage("File Uploaded.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
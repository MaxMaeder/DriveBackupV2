package ratismal.drivebackup;

import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.googledrive.GoogleUploader;
import ratismal.drivebackup.onedrive.OneDriveUploader;
import ratismal.drivebackup.util.FileUtil;
import ratismal.drivebackup.util.MessageUtil;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

            MessageUtil.sendConsoleMessage("Doing backups for " + type);
            if (create.equalsIgnoreCase("true")) {
                FileUtil.makeBackup(type, format);
            }

            File file = FileUtil.getFileToUpload(type, format, false);
            DecimalFormat df = new DecimalFormat("#.##");
            try {
                if (Config.isGoogleEnabled()) {
                    MessageUtil.sendConsoleMessage("Uploading file to GoogleDrive");
                    Date startTime = new Date();
                    GoogleUploader.uploadFile(file, type);
                    Date endTime = new Date();
                    double difference = endTime.getTime() - startTime.getTime();

                    double length = Double.valueOf(df.format(difference / 1000));
                    double speed = Double.valueOf(df.format((file.length() / 1024) / length));

                    MessageUtil.sendConsoleMessage("File uploaded in " +
                            length + " seconds (" + speed + " KB/s)");
                }
                if (Config.isOnedriveEnabled()) {
                    MessageUtil.sendConsoleMessage("Uploading file to OneDrive");
                    //Couldn't get around static issue, declared a new Instance.
                    OneDriveUploader onedrive = new OneDriveUploader();
                    Date startTime = new Date();
                    onedrive.uploadFile(file, type);
                    Date endTime = new Date();
                    double difference = endTime.getTime() - startTime.getTime();
                    double length = Double.valueOf(df.format(difference / 1000));
                    double speed = Double.valueOf(df.format((file.length() / 1024) / length));

                    MessageUtil.sendConsoleMessage("File uploaded in " +
                            length + " seconds (" + +speed + " KB/s)");
                }
                //MessageUtil.sendConsoleMessage("File Uploaded.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        MessageUtil.sendMessageToAllPlayers("Backup complete. The next backup is in " + Config.getBackupDelay() / 20 / 60 + " minutes.");

    }

}
package ratismal.drivebackup;

import org.bukkit.Bukkit;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.googledrive.GoogleUploader;
import ratismal.drivebackup.handler.PlayerListener;
import ratismal.drivebackup.onedrive.OneDriveUploader;
import ratismal.drivebackup.util.FileUtil;
import ratismal.drivebackup.util.MessageUtil;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Ratismal on 2016-01-22.
 */

public class UploadThread implements Runnable {

    private boolean forced = false;

    public UploadThread(boolean forced) {
        this.forced = forced;
    }

    public UploadThread() {
    }

    private void getTimes(Date start, Date end, File file){
        DecimalFormat df = new DecimalFormat("#.##");
        double difference = end.getTime() - start.getTime();
        double length = Double.valueOf(df.format(difference / 1000));
        double speed = Double.valueOf(df.format((file.length() / 1024) / length));

        MessageUtil.sendConsoleMessage("File uploaded in " +
                length + " seconds (" + speed + "KB/s)");
    }

    @Override
    public void run() {
        if (PlayerListener.doBackups || forced) {
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

                try {
                    if (Config.isGoogleEnabled()) {
                        MessageUtil.sendConsoleMessage("Uploading file to GoogleDrive");
                        Date startTime = new Date();
                        GoogleUploader.uploadFile(file, type);
                        Date endTime = new Date();
                        getTimes(startTime, endTime, file);

                    }
                    if (Config.isOnedriveEnabled()) {
                        MessageUtil.sendConsoleMessage("Uploading file to OneDrive");
                        //Couldn't get around static issue, declared a new Instance.
                        OneDriveUploader onedrive = new OneDriveUploader();
                        Date startTime = new Date();
                        onedrive.uploadFile(file, type);
                        Date endTime = new Date();
                        getTimes(startTime, endTime, file);
                    }

                    if(!Config.keepLocalBackup()){
                        if (file.delete()) {
                            MessageUtil.sendConsoleMessage("Old backup deleted.");
                        } else {
                            MessageUtil.sendConsoleMessage("Failed to delete backup " + file.getAbsolutePath());
                        }
                    }
                    //MessageUtil.sendConsoleMessage("File Uploaded.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (forced) {
                MessageUtil.sendMessageToAllPlayers("Backup complete.");
            } else {
                MessageUtil.sendMessageToAllPlayers("Backup complete. The next backup is in " + Config.getBackupDelay() / 20 / 60 + " minutes.");
            }
            if (Bukkit.getOnlinePlayers().size() == 0 && PlayerListener.doBackups) {
                MessageUtil.sendMessageToAllPlayers("Disabling automatic backups due to inactivity.");
                    PlayerListener.doBackups = false;
            }
        } else {
            MessageUtil.sendConsoleMessage("Skipping backup.");
        }
    }

}
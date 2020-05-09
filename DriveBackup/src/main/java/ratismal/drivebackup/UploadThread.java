package ratismal.drivebackup;

import org.bukkit.Bukkit;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.ftp.FTPUploader;
import ratismal.drivebackup.googledrive.GoogleUploader;
import ratismal.drivebackup.handler.PlayerListener;
import ratismal.drivebackup.onedrive.OneDriveUploader;
import ratismal.drivebackup.util.*;
import ratismal.drivebackup.util.Timer;

import java.io.File;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Created by Ratismal on 2016-01-22.
 */

public class UploadThread implements Runnable {

    private boolean forced = false;

    /**
     * Forced upload constructor
     *
     * @param forced Is the backup forced?
     */
    public UploadThread(boolean forced) {
        this.forced = forced;
    }

    /**
     * Base constructor
     */
    public UploadThread() {
    }

    /**
     * Run function in the upload thread
     */
    @Override
    public void run() {
        if (PlayerListener.doBackups || forced) {
            MessageUtil.sendMessageToAllPlayers(Config.getBackupStart());
            // Create Backup Here
            HashMap<String, HashMap<String, Object>> backupList = Config.getBackupList();
            for (Map.Entry<String, HashMap<String, Object>> set : backupList.entrySet()) {

                String type = set.getKey();
                String format = set.getValue().get("format").toString();
                String create = set.getValue().get("create").toString();

                List<String> blackList = new ArrayList<>();
                if (set.getValue().containsKey("blacklist")) {
                    Object tempObject = set.getValue().get("blacklist");
                    if (tempObject instanceof List<?>) {
                        blackList = (List<String>) tempObject;
                    }
                }

                MessageUtil.sendConsoleMessage("Doing backups for " + type);
                if (create.equalsIgnoreCase("true")) {
                    FileUtil.makeBackup(type, format, blackList);
                }

                File file = FileUtil.getFileToUpload(type, format, false);
                ratismal.drivebackup.util.Timer timer = new Timer();
                try {
                    if (Config.isGoogleEnabled()) {
                        MessageUtil.sendConsoleMessage("Uploading file to GoogleDrive");
                        timer.start();
                        GoogleUploader.uploadFile(file, type);
                        timer.end();
                        MessageUtil.sendConsoleMessage(timer.getUploadTimeMessage(file));
                    }
                    if (Config.isOnedriveEnabled()) {
                        MessageUtil.sendConsoleMessage("Uploading file to OneDrive");
                        //Couldn't get around static issue, declared a new Instance.
                        OneDriveUploader oneDrive = new OneDriveUploader();
                        timer.start();
                        oneDrive.uploadFile(file, type);
                        timer.end();
                        MessageUtil.sendConsoleMessage(timer.getUploadTimeMessage(file));
                    }
                    if (Config.isFtpEnabled()) {
                        MessageUtil.sendConsoleMessage("Uploading file to FTP");
                        timer.start();
                        FTPUploader.uploadFile(file, type);
                        timer.end();
                        MessageUtil.sendConsoleMessage(timer.getUploadTimeMessage(file));
                    }

                    if (!Config.keepLocalBackup()) {
                        if (file.delete()) {
                            MessageUtil.sendConsoleMessage("Old backup deleted.");
                        } else {
                            MessageUtil.sendConsoleMessage("Failed to delete backup " + file.getAbsolutePath());
                        }
                    }
                    //MessageUtil.sendConsoleMessage("File Uploaded.");
                } catch (Exception e) {
                    if (Config.isDebug())
                        e.printStackTrace();
                }
            }
            if (forced) {
                MessageUtil.sendMessageToAllPlayers(Config.getBackupDone());
            } else {
                MessageUtil.sendMessageToAllPlayers(Config.getBackupDone() + " " + Config.getBackupNext().replaceAll("%TIME", String.valueOf(Config.getBackupDelay() / 20 / 60)));
            }
            if (Bukkit.getOnlinePlayers().size() == 0 && PlayerListener.doBackups) {
                MessageUtil.sendConsoleMessage("Disabling automatic backups due to inactivity.");
                PlayerListener.doBackups = false;
            }
        } else {
            MessageUtil.sendConsoleMessage("Skipping backup.");
        }
    }

}
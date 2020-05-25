package ratismal.drivebackup;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.ftp.FTPUploader;
import ratismal.drivebackup.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.handler.PlayerListener;
import ratismal.drivebackup.onedrive.OneDriveUploader;
import ratismal.drivebackup.util.*;
import ratismal.drivebackup.util.Timer;

import java.io.File;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
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
        Thread.currentThread().setPriority(Thread.NORM_PRIORITY + Config.getBackupThreadPriority());

        if (PlayerListener.doBackups || forced) {
            MessageUtil.sendMessageToAllPlayers(Config.getBackupStart());

            GoogleDriveUploader googleDriveUploader = new GoogleDriveUploader();
            OneDriveUploader oneDriveUploader = new OneDriveUploader();
            FTPUploader FTPUploader = new FTPUploader();

            // Create Backup Here
            ArrayList<HashMap<String, Object>> backupList = Config.getBackupList();
            for (HashMap<String, Object> set : backupList) {

                String type = set.get("path").toString();
                String format = set.get("format").toString();
                String create = set.get("create").toString();

                List<String> blackList = new ArrayList<>();
                if (set.containsKey("blacklist")) {
                    Object tempObject = set.get("blacklist");
                    if (tempObject instanceof List<?>) {
                        blackList = (List<String>) tempObject;
                    }
                }

                MessageUtil.sendConsoleMessage("Doing backups for " + type);
                if (create.equalsIgnoreCase("true")) {
                    try {
                        FileUtil.makeBackup(type, format, blackList);
                    } catch(Exception error) {
                        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                            if (!player.hasPermission("drivebackup.linkAccounts")) continue;

                            MessageUtil.sendMessage(player, "Failed to create backup, path to folder to backup is absolute, expected a relative path");
                            MessageUtil.sendMessage(player, "An absolute path can overwrite sensitive files, see the " + ChatColor.GOLD + "config.yml " + ChatColor.DARK_AQUA + "for more information");
                        }

                        return;
                    }
                }

                File file = FileUtil.getFileToUpload(type, format, false);
                ratismal.drivebackup.util.Timer timer = new Timer();
                try {
                    if (Config.isGoogleEnabled()) {
                        MessageUtil.sendConsoleMessage("Uploading file to Google Drive");
                        timer.start();
                        googleDriveUploader.uploadFile(file, type);
                        timer.end();
                        MessageUtil.sendConsoleMessage(timer.getUploadTimeMessage(file));
                    }
                    if (Config.isOnedriveEnabled()) {
                        MessageUtil.sendConsoleMessage("Uploading file to OneDrive");
                        timer.start();
                        oneDriveUploader.uploadFile(file, type);
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

                    FileUtil.deleteFiles(type, format);
                } catch (Exception e) {
                    MessageUtil.sendConsoleException(e);
                }
            }

            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            	if (!player.hasPermission("drivebackup.linkAccounts")) continue;
                
                if (Config.isGoogleEnabled()) {
                    if (googleDriveUploader.isErrorWhileUploading()) {
                        MessageUtil.sendMessage(player, "Failed to backup to Google Drive, please run " + ChatColor.GOLD + "/drivebackup linkaccount googledrive");
                    } else {
                        MessageUtil.sendMessage(player, "Backup to " + ChatColor.GOLD + "Google Drive " + ChatColor.DARK_AQUA + "complete");
                    }
                }
                if (Config.isOnedriveEnabled()) {
                    if (oneDriveUploader.isErrorWhileUploading()) {
                        MessageUtil.sendMessage(player, "Failed to backup to OneDrive, please run " + ChatColor.GOLD + "/drivebackup linkaccount onedrive");
                    } else {
                        MessageUtil.sendMessage(player, "Backup to " + ChatColor.GOLD + "OneDrive " + ChatColor.DARK_AQUA + "complete");
                    }
                }
                if (Config.isFtpEnabled()) {
                    if (FTPUploader.isErrorWhileUploading()) {
                        MessageUtil.sendMessage(player, "Failed to backup to the SFTP/FTP server, please check the server credentials in the " + ChatColor.GOLD + "config.yml");
                    } else {
                        MessageUtil.sendMessage(player, "Backup to the " + ChatColor.GOLD + "SFTP/FTP server " + ChatColor.DARK_AQUA + "complete");
                    }
                }
            }

            if (forced) {
                MessageUtil.sendMessageToAllPlayers(Config.getBackupDone());
            } else {
                String nextBackupMessage = "";

                if (Config.isBackupsScheduled()) {

                    LocalDateTime nextBackupDate = null;

                    LocalDateTime now = LocalDateTime.now(Config.getBackupScheduleTimezone());

                    int weeksCheckedForDate;
                    for (weeksCheckedForDate = 0; weeksCheckedForDate < 2; weeksCheckedForDate++) {
                        for (LocalDateTime date : DriveBackup.getBackupDatesList()) {

                            if (nextBackupDate == null &&

                                ((LocalTime.from(date).isAfter(LocalTime.from(now)) && // This might not work if time specified is 00:00
                                date.getDayOfWeek().compareTo(now.getDayOfWeek()) == 0) ||

                                date.getDayOfWeek().compareTo(now.getDayOfWeek()) > 0)
                            ) {
                                nextBackupDate = date;
                                continue;
                            }

                            if (nextBackupDate != null &&

                                ((LocalTime.from(date).isBefore(LocalTime.from(nextBackupDate)) && // This might not work if time specified is 00:00
                                LocalTime.from(date).isAfter(LocalTime.from(now)) &&
                                (date.getDayOfWeek().compareTo(nextBackupDate.getDayOfWeek()) == 0 ||
                                date.getDayOfWeek().compareTo(now.getDayOfWeek()) == 0)) || 

                                (date.getDayOfWeek().compareTo(nextBackupDate.getDayOfWeek()) < 0 &&
                                date.getDayOfWeek().compareTo(now.getDayOfWeek()) > 0))
                            ) {
                                nextBackupDate = date;
                            }
                        }

                        if (nextBackupDate != null) {
                            break;
                        }

                        now = now
                            .with(ChronoField.DAY_OF_WEEK, 1)
                            .with(ChronoField.CLOCK_HOUR_OF_DAY, 1)
                            .with(ChronoField.MINUTE_OF_HOUR, 0)
                            .with(ChronoField.SECOND_OF_DAY, 0);
                    }

                    if (weeksCheckedForDate == 1) {
                        nextBackupDate = nextBackupDate
                            .with(ChronoField.YEAR, now.get(ChronoField.YEAR))
                            .with(ChronoField.ALIGNED_WEEK_OF_YEAR, now.get(ChronoField.ALIGNED_WEEK_OF_YEAR) + 1);
                    } else {
                        nextBackupDate = nextBackupDate
                            .with(ChronoField.YEAR, now.get(ChronoField.YEAR))
                            .with(ChronoField.ALIGNED_WEEK_OF_YEAR, now.get(ChronoField.ALIGNED_WEEK_OF_YEAR));
                    }

                    nextBackupMessage = Config.getBackupNextScheduled().replaceAll("%DATE", nextBackupDate.format(DateTimeFormatter.ofPattern(Config.getBackupNextScheduledFormat())));
                } else if (Config.getBackupDelay() / 60 / 20 != -1) {
                    nextBackupMessage = Config.getBackupNext().replaceAll("%TIME", String.valueOf(Config.getBackupDelay() / 20 / 60));
                }

                MessageUtil.sendMessageToAllPlayers(Config.getBackupDone() + " " + nextBackupMessage);
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
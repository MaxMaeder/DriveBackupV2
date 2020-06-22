package ratismal.drivebackup;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.text.TextComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.ftp.FTPUploader;
import ratismal.drivebackup.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.handler.PlayerListener;
import ratismal.drivebackup.mysql.MySQLUploader;
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
    private CommandSender initiator;

    /**
     * Creates an instance of the {@code UploadThread} object
     */
    public UploadThread() {
    }

    /**
     * Creates an instance of the {@code UploadThread} object
     * @param initiator the player who initiated the backup
     */
    public UploadThread(CommandSender initiator) {
        this.initiator = initiator;
    }

    /**
     * Starts a backup
     */
    @Override
    public void run() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY + Config.getBackupThreadPriority());

        if (!Config.isBackupsRequirePlayers() || PlayerListener.isAutoBackupsActive() || initiator != null) {
            if (!Config.isGoogleEnabled() && !Config.isOnedriveEnabled() && !Config.isFtpEnabled()) {
                ArrayList<Player> playersToNotify = new ArrayList<>();

                if (initiator != null) {
                    playersToNotify.add((Player) initiator);
                }

                for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                    if (player.hasPermission("drivebackup.linkAccounts") && !playersToNotify.contains(player)) {
                        playersToNotify.add(player);
                    }
                }

                for (Player player : playersToNotify) {
                    MessageUtil.sendMessage(player, "No backup method is enabled");
                }

                return;
            }

            MessageUtil.sendMessageToAllPlayers(Config.getBackupStart());

            GoogleDriveUploader googleDriveUploader = null;
            OneDriveUploader oneDriveUploader = null;
            FTPUploader ftpUploader = null;

            if (Config.isGoogleEnabled()) {
                googleDriveUploader = new GoogleDriveUploader();
            }
            if (Config.isOnedriveEnabled()) {
                oneDriveUploader = new OneDriveUploader();
            }
            if (Config.isFtpEnabled()) {
                ftpUploader = new FTPUploader();
            }

            ArrayList<HashMap<String, Object>> backupList = Config.getBackupList();

            ArrayList<HashMap<String, Object>> externalBackupList = Config.getExternalBackupList();
            if (externalBackupList != null) {
                
                for (HashMap<String, Object> externalBackup : externalBackupList) {
                    switch ((String) externalBackup.get("type")) {
                        case "ftpServer":
                        case "ftpsServer":
                        case "sftpServer":
                            makeExternalFileBackup(externalBackup, backupList);
                            break;
                        case "mysqlDatabase":
                            makeExternalDatabaseBackup(externalBackup, backupList);
                            break;
                    }
                }
            }
            
            for (HashMap<String, Object> set : backupList) {

                String type = set.get("path").toString();
                String format = set.get("format").toString();
                String create = set.get("create").toString();

                ArrayList<String> blackList = new ArrayList<>();
                if (set.containsKey("blacklist")) {
                    Object tempObject = set.get("blacklist");
                    if (tempObject instanceof List<?>) {
                        blackList = (ArrayList<String>) tempObject;
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

                if (googleDriveUploader != null) {
                    MessageUtil.sendConsoleMessage("Uploading file to Google Drive");
                    timer.start();
                    googleDriveUploader.uploadFile(file, type);
                    timer.end();
                    MessageUtil.sendConsoleMessage(timer.getUploadTimeMessage(file));
                }
                if (oneDriveUploader != null) {
                    MessageUtil.sendConsoleMessage("Uploading file to OneDrive");
                    timer.start();
                    oneDriveUploader.uploadFile(file, type);
                    timer.end();
                    MessageUtil.sendConsoleMessage(timer.getUploadTimeMessage(file));
                }
                if (ftpUploader != null) {
                    MessageUtil.sendConsoleMessage("Uploading file to the (S)FTP server");
                    timer.start();
                    ftpUploader.uploadFile(file, type);
                    timer.end();
                    MessageUtil.sendConsoleMessage(timer.getUploadTimeMessage(file));
                }

                try {
                    FileUtil.deleteFiles(type, format);
                } catch (Exception exception) {
                    MessageUtil.sendConsoleException(exception);
                } 
            }

            FileUtil.deleteFolder(new File("external-backups"));

            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            	if (!player.hasPermission("drivebackup.linkAccounts")) continue;
                
                if (googleDriveUploader != null) {
                    if (googleDriveUploader.isErrorWhileUploading()) {
                        MessageUtil.sendMessage(player, TextComponent.builder()
                        .append(
                            TextComponent.of("Failed to back up to Google Drive, please run ")
                            .color(TextColor.DARK_AQUA)
                        )
                        .append(
                            TextComponent.of("/drivebackup linkaccount googledrive")
                            .color(TextColor.GOLD)
                            .hoverEvent(HoverEvent.showText(TextComponent.of("Run command")))
                            .clickEvent(ClickEvent.runCommand("/drivebackup linkaccount googledrive"))
                        )
                        .build());
                    } else {
                        MessageUtil.sendMessage(player, "Backup to " + ChatColor.GOLD + "Google Drive " + ChatColor.DARK_AQUA + "complete");
                    }
                }

                if (oneDriveUploader != null) {
                    if (oneDriveUploader.isErrorWhileUploading()) {
                        MessageUtil.sendMessage(player, TextComponent.builder()
                        .append(
                            TextComponent.of("Failed to back up to OneDrive, please run ")
                            .color(TextColor.DARK_AQUA)
                        )
                        .append(
                            TextComponent.of("/drivebackup linkaccount onedrive")
                            .color(TextColor.GOLD)
                            .hoverEvent(HoverEvent.showText(TextComponent.of("Run command")))
                            .clickEvent(ClickEvent.runCommand("/drivebackup linkaccount onedrive"))
                        )
                        .build());
                    } else {
                        MessageUtil.sendMessage(player, "Backup to " + ChatColor.GOLD + "OneDrive " + ChatColor.DARK_AQUA + "complete");
                    }
                }

                if (ftpUploader != null) {
                    ftpUploader.close();

                    if (ftpUploader.isErrorWhileUploading()) {
                        MessageUtil.sendMessage(player, "Failed to back up to the (S)FTP server, please check the server credentials in the " + ChatColor.GOLD + "config.yml");
                    } else {
                        MessageUtil.sendMessage(player, "Backup to the " + ChatColor.GOLD + "(S)FTP server " + ChatColor.DARK_AQUA + "complete");
                    }
                }
            }

            if (initiator != null) {
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

                    nextBackupMessage = Config.getBackupNextScheduled().replaceAll("%DATE", nextBackupDate.format(DateTimeFormatter.ofPattern(Config.getBackupNextScheduledFormat(), new Locale(Config.getDateLanguage()))));
                } else if (Config.getBackupDelay() / 60 / 20 != -1) {
                    nextBackupMessage = Config.getBackupNext().replaceAll("%TIME", String.valueOf(Config.getBackupDelay() / 20 / 60));
                }

                MessageUtil.sendMessageToAllPlayers(Config.getBackupDone() + " " + nextBackupMessage);
            }
            if (Config.isBackupsRequirePlayers() && Bukkit.getOnlinePlayers().size() == 0 && PlayerListener.isAutoBackupsActive()) {
                MessageUtil.sendConsoleMessage("Disabling automatic backups due to inactivity");
                PlayerListener.setAutoBackupsActive(false);
            }
        } else {
            MessageUtil.sendConsoleMessage("Skipping backup due to inactivity");
        }
    }

    /**
     * Downloads files from a FTP server and stores them within the external-backups temporary folder, using the specified external backup settings
     * @param externalBackup the external backup settings
     * @param backupList the list of folders to upload to the configured remote destinations
     */
    private static void makeExternalFileBackup(HashMap<String, Object> externalBackup, ArrayList<HashMap<String, Object>> backupList) {
        MessageUtil.sendConsoleMessage("Downloading files from a (S)FTP server (" + getSocketAddress(externalBackup) + ") to include in backup");

        FTPUploader ftpUploader = new FTPUploader(
                (String) externalBackup.get("hostname"), 
                (int) externalBackup.get("port"), 
                (String) externalBackup.get("username"), 
                (String) externalBackup.get("password"),
                externalBackup.get("type").equals("ftpsServer"),
                externalBackup.get("type").equals("sftpServer"), 
                (String) externalBackup.get("sftp-public-key"), 
                (String) externalBackup.get("sftp-passphrase"),
                "external-backups",
                ".");

        for (Map<String, Object> backup : (List<Map<String, Object>>) externalBackup.get("backup-list")) {
            ArrayList<String> blackList = new ArrayList<>();
            if (backup.containsKey("blacklist")) {
                Object tempObject = backup.get("blacklist");
                if (tempObject instanceof List<?>) {
                    blackList = (ArrayList<String>) tempObject;
                }
            }

            for (String filePath : ftpUploader.getFiles(externalBackup.get("base-dir") + File.separator + backup.get("path"))) {
                if (blackList.contains(new File(filePath).getName())) {
                    continue;
                }

                String parentFolder = new File(filePath).getParent();
                String parentFolderPath;
                if (parentFolder != null) {
                    parentFolderPath = File.separator + parentFolder;
                } else {
                    parentFolderPath = "";
                }

                ftpUploader.downloadFile(externalBackup.get("base-dir") + File.separator + backup.get("path") + File.separator + filePath, getTempFolderName(externalBackup) + File.separator + backup.get("path") + parentFolderPath);
            }
        }

        ftpUploader.close();

        HashMap<String, Object> backup = new HashMap<>();
        backup.put("path", "external-backups" + File.separator + getTempFolderName(externalBackup));
        backup.put("format", externalBackup.get("format"));
        backup.put("create", "true");
        backupList.add(backup);

        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (!player.hasPermission("drivebackup.linkAccounts")) continue;

            if (ftpUploader.isErrorWhileUploading()) {
                MessageUtil.sendMessage(player, "Failed to include files from a (S)FTP server (" + getSocketAddress(externalBackup) + ") in the backup, please check the server credentials in the " + ChatColor.GOLD + "config.yml");
            } else {
                MessageUtil.sendMessage(player, "Files from a " + ChatColor.GOLD + "(S)FTP server (" + getSocketAddress(externalBackup) + ") " + ChatColor.DARK_AQUA + "were successfully included in the backup");
            }
        }
    }

    /**
     * Downloads databases from a MySQL server and stores them within the external-backups temporary folder, using the specified external backup settings
     * @param externalBackup the external backup settings
     * @param backupList the list of folders to upload to the configured remote destinations
     */
    private static void makeExternalDatabaseBackup(HashMap<String, Object> externalBackup, ArrayList<HashMap<String, Object>> backupList) {
        MessageUtil.sendConsoleMessage("Downloading databases from a MySQL server (" + getSocketAddress(externalBackup) + ") to include in backup");

        MySQLUploader mysqlUploader = new MySQLUploader(
                (String) externalBackup.get("hostname"), 
                (int) externalBackup.get("port"), 
                (String) externalBackup.get("username"), 
                (String) externalBackup.get("password"));

        for (String databaseName : (List<String>) externalBackup.get("names")) {
            mysqlUploader.downloadDatabase(databaseName, getTempFolderName(externalBackup));
        }

        HashMap<String, Object> backup = new HashMap<>();
        backup.put("path", "external-backups" + File.separator + getTempFolderName(externalBackup));
        backup.put("format", externalBackup.get("format"));
        backup.put("create", "true");
        backupList.add(backup);

        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (!player.hasPermission("drivebackup.linkAccounts")) continue;

            if (mysqlUploader.isErrorWhileUploading()) {
                MessageUtil.sendMessage(player, "Failed to include databases from a MySQL server (" + getSocketAddress(externalBackup) + ") in the backup, please check the server credentials in the " + ChatColor.GOLD + "config.yml");
            } else {
                MessageUtil.sendMessage(player, "Databases from a " + ChatColor.GOLD + "MySQL server (" + getSocketAddress(externalBackup) + ") " + ChatColor.DARK_AQUA + "were successfully included in the backup");
            }
        }
    }

    /**
     * Gets the socket address (ipaddress/hostname:port) of an external backup server based on the specified settings
     * @param externalBackup the external backup settings
     * @return the socket address
     */
    private static String getSocketAddress(HashMap<String, Object> externalBackup) {
        return externalBackup.get("hostname") + ":" + externalBackup.get("port");
    }

    /**
     * Generates the name for a folder based on the specified external backup settings to be stored within the external-backups temporary folder
     * @param externalBackup the external backup settings
     * @return the folder name
     */
    private static String getTempFolderName(HashMap<String, Object> externalBackup) {
        if (externalBackup.get("type").equals("mysqlDatabase")) {
            return "mysql-" + getSocketAddress(externalBackup);
        } else {
            return "ftp-" + getSocketAddress(externalBackup);
        }
    }
}
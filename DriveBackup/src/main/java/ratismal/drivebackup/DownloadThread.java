package ratismal.drivebackup;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.text.TextComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.ftp.FTPUploader;
import ratismal.drivebackup.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.onedrive.OneDriveUploader;
import ratismal.drivebackup.util.*;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Ratismal on 2016-01-22.
 */

public class DownloadThread implements Runnable {
    private DriveBackup plugin;
    private CommandSender initiator;
    private String[] args;

    /**
     * Creates an instance of the {@code DownloadThread} object
     * @param plugin a reference to the {@code DriveBackup} plugin
     * @param initiator the player who initiated the restore to a backup process
     * @param args any arguments that followed the command that initiated the restore to a backup process
     */
    public DownloadThread(DriveBackup plugin, CommandSender initiator, String[] args) {
        this.plugin = plugin;
        this.initiator = initiator;
        this.args = args;
    }

    /**
     * Starts the restore to a backup process
     */
    @Override
    public void run() {
        System.out.println("HI");

        Thread.currentThread().setPriority(Thread.MIN_PRIORITY + Config.getBackupThreadPriority());

        if (args.length > 1 && args[1].equals("canceled")) {
            MessageUtil.sendMessage(initiator, "Canceled the backup restore process");

            return;
        }

        // args[1] = the optimal creation time of the backup to restore
        // args[2] = the optimal creation date of the backup to restore
        // args[3] = whether to restore the closest backup to the optimal time/date, the closest before, or the closest overall
        Date targetDate;
        try {
            targetDate = new SimpleDateFormat("kk:mm MM/dd/yyyy").parse(args[1] + " " + args[2]);
        } catch (Exception error) {
            MessageUtil.sendConsoleException(error);
            MessageUtil.sendMessage(initiator, "Please specify the approximate time/date of the backup you want to restore from");

            return;
        }
        
        String targetDateRelation;
        if (args.length < 4) {
            targetDateRelation = "closest";
        } else {
            targetDateRelation = args[3];
        }

        GoogleDriveUploader googleDriveUploader = null;
        OneDriveUploader oneDriveUploader = null;
        FTPUploader ftpUploader = null;

        if (Config.isGoogleEnabled()) {
            googleDriveUploader = new GoogleDriveUploader("restored-backup", Config.getDestination());
        }
        if (Config.isOnedriveEnabled()) {
            oneDriveUploader = new OneDriveUploader("restored-backup", Config.getDestination());
        }
        if (Config.isOnedriveEnabled()) {
            ftpUploader = new FTPUploader(
                Config.getFtpHost(), 
                Config.getFtpPort(), 
                Config.getFtpUser(), 
                Config.getFtpPass(), 
                Config.isFtpFtps(), 
                Config.isFtpSftp(), 
                Config.getSftpPublicKey(), 
                Config.getSftpPass(), 
                "restored-backup",
                Config.getDestination());
        }

        ArrayList<HashMap<String, Object>> closestSavedBackupList = new ArrayList<>();
        ArrayList<HashMap<String, Object>> backupList = Config.getBackupList();

        for (HashMap<String, Object> backup : backupList) {
            String backupPath = backup.get("path").toString();

            ArrayList<HashMap<String, Object>> savedBackupList = new ArrayList<>();

            if (oneDriveUploader != null) {
                addFilesToList(savedBackupList, oneDriveUploader.getZipFiles(backupPath), backupPath, "oneDrive");
            }

            if (googleDriveUploader != null) {
                addFilesToList(savedBackupList, googleDriveUploader.getZipFiles(backupPath), backupPath, "googleDrive");
            }

            if (ftpUploader != null) {
                addFilesToList(savedBackupList, ftpUploader.getZipFiles(backupPath), backupPath, "ftpServer");
            }

            if (Config.getLocalKeepCount() != 0) {
                addFilesToList(savedBackupList, FileUtil.getZipFiles(backupPath), backupPath, "local");
            }

            Collections.sort(savedBackupList, new Comparator() {
                @Override
                public int compare(Object _savedBackup, Object _nextSavedBackup) {
                    HashMap<String,Object> savedBackup = (HashMap<String,Object>) _savedBackup;
                    HashMap<String,Object> nextSavedBackup = (HashMap<String,Object>) _nextSavedBackup;

                    int dateDifference = (int) (((Date) savedBackup.get("lastModified")).getTime() - ((Date) nextSavedBackup.get("lastModified")).getTime());
    
                    if (dateDifference != 0) {
                        return dateDifference;
                    }
    
                    int savedBackupLocationPriority = 0;
                    int nextSavedBackupLocationPriority = 0;
    
                    switch ((String) savedBackup.get("location")) {
                        case "oneDrive": savedBackupLocationPriority = 0;
                        case "googleDrive": savedBackupLocationPriority = 1;
                        case "ftpServer": savedBackupLocationPriority = 2;
                        case "local": savedBackupLocationPriority = 3;
                    }
    
                    switch ((String) nextSavedBackup.get("location")) {
                        case "oneDrive": nextSavedBackupLocationPriority = 0;
                        case "googleDrive": nextSavedBackupLocationPriority = 1;
                        case "ftpServer": nextSavedBackupLocationPriority = 2;
                        case "local": nextSavedBackupLocationPriority = 3;
                    }
    
                    return savedBackupLocationPriority - nextSavedBackupLocationPriority;
                }
            });
    
            HashMap<String,Object> closestSavedBackup = null;
    
            for (HashMap<String,Object> savedBackup : savedBackupList) {
                if (closestSavedBackup == null) {
                    closestSavedBackup = savedBackup;
    
                    continue;
                }
    
                long targetDateTime = targetDate.getTime();
                long closestSavedBackupTime = ((Date) closestSavedBackup.get("lastModified")).getTime();
                long savedBackupTime = ((Date) savedBackup.get("lastModified")).getTime();
    
                if (Math.abs(savedBackupTime - targetDateTime) < Math.abs(closestSavedBackupTime - targetDateTime) &&
    
                    (targetDateRelation == "closest" ||
                    (targetDateRelation == "closestBefore" && savedBackupTime < targetDateTime) || 
                    (targetDateRelation == "closestAfter" && savedBackupTime > targetDateTime))
                ) {
                    closestSavedBackup = savedBackup;
                }
            }

            if (closestSavedBackup == null) {
                MessageUtil.sendMessage(initiator, "Cannot find a file to restore " + backupPath + " from");

                backupList.remove(backup);
                if (backupList.size() == 0) {
                    return;
                }

                StringBuilder alternateCommand = new StringBuilder();
                alternateCommand.append("To restore without this, run " + ChatColor.GOLD + "/drivebackup backup " + args[0] + " " + args[1] + " " + targetDateRelation + " ");
                for (HashMap<String,Object> backupToAdd : backupList) {
                    alternateCommand.append(backupToAdd.get("type"));
                }
                MessageUtil.sendMessage(initiator, alternateCommand.toString());

                return;
            }

            closestSavedBackupList.add(closestSavedBackup);
        }

        // args[last] = whether the user has confirmed that this is the backup they want to restore from
        if (!args[args.length - 1].equals("confirmed")) {
            for (HashMap<String,Object> closestSavedBackup : closestSavedBackupList) {
                MessageUtil.sendMessage(initiator, "Will restore " + closestSavedBackup.get("type") + " from a backup on " + new SimpleDateFormat("h:mm a MM/dd/yyyy").format(closestSavedBackup.get("lastModified")));
            }
            

            StringBuilder confirmCommand = new StringBuilder();
            confirmCommand.append("/drivebackup ");

            for (String arg : args) {
                confirmCommand.append(arg + " ");
            }
            confirmCommand.append("confirmed");

            MessageUtil.sendMessage(initiator, TextComponent.builder()
            .append(
                TextComponent.of("Continue restoring from these backups?")
                .color(TextColor.DARK_AQUA)
            )
            .append(
                TextComponent.of(" [YES]")
                .color(TextColor.GOLD)
                .hoverEvent(HoverEvent.showText(TextComponent.of("Continue restoring from the backup")))
                .clickEvent(ClickEvent.runCommand(confirmCommand.toString()))
            )
            .append(
                TextComponent.of(" [NO]")
                .color(TextColor.GOLD)
                .hoverEvent(HoverEvent.showText(TextComponent.of("Cancel restoring from the backup")))
                .clickEvent(ClickEvent.runCommand("/drivebackup restore canceled"))
            )
            .build());
            
            return;
        } else {

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.kickPlayer("Please wait while we restore to a backup");
            }

            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                @Override
                public void run() {

                    boolean worldsUnloadedSuccessfully = true;

                    for (World world : Bukkit.getWorlds()) {
                        for(Chunk chunk : world.getLoadedChunks()) {
                            chunk.unload();
                        }        

                        if (Bukkit.unloadWorld(world, false) == false) {
                            worldsUnloadedSuccessfully = false;
                        }
                    }

                    

                    if (!worldsUnloadedSuccessfully) {
                        MessageUtil.sendMessage(initiator, "Failed to unload all worlds");

                        return;
                    }

                }
            }, 3);

            boolean localFileErrorOccurred = false;

                    for (HashMap<String,Object> closestSavedBackup : closestSavedBackupList) {
                        String path = (String) closestSavedBackup.get("filePath");
                        String type = (String) closestSavedBackup.get("type");

                        switch((String) closestSavedBackup.get("location")) {
                            case "oneDrive":
                                oneDriveUploader.downloadFile(path, type);
                                break;
                            case "googleDrive":
                                googleDriveUploader.downloadFile(path, type);
                                break;
                            case "ftpServer":
                                ftpUploader.downloadFile(path, type);
                                break;
                            case "local":
                                try {
                                    FileUtil.copyFolder(path, type);
                                } catch (IOException exception) {
                                    MessageUtil.sendConsoleException(exception);
                                    localFileErrorOccurred = true;
                                }
                                break;
                        }
                    }

                    if (oneDriveUploader.isErrorWhileUploading() || googleDriveUploader.isErrorWhileUploading() || ftpUploader.isErrorWhileUploading() || localFileErrorOccurred) {
                        MessageUtil.sendMessage(initiator, "Failed to download the files necessary to restore to the backup");
                        FileUtil.deleteFolder(new File("restored-backup"));

                        return;
                    }

                    boolean extractedFilesSuccessfully = true;

                    for (HashMap<String,Object> closestSavedBackup : closestSavedBackupList) {
                        String type = (String) closestSavedBackup.get("type");

                        try {
                            FileUtil.extractFolder("restored-backup" + File.separator + type, type);
                        } catch (IOException exception) {
                            MessageUtil.sendConsoleException(exception);
                            extractedFilesSuccessfully = false;
                        }
                    }

                    if (extractedFilesSuccessfully) {
                        MessageUtil.sendMessage(initiator, "Backup restored successfully!");
                    } else {
                        MessageUtil.sendMessage(initiator, "Failed to extract the files necessary to restore to the backup");
                    }

                    FileUtil.deleteFolder(new File("restored-backup"));
        }
    }

    /**
     * Adds all of the specifed backup files to the specifed list
     * @param fileList the list to add the files to
     * @param files the file paths/modification dates of the backup files to add
     * @param type the type of files (ex. plugins, world)
     * @param backupLocation the location where the backup files were stored (Ex. googleDrive, local)
     */
    private static void addFilesToList(ArrayList<HashMap<String, Object>> fileList, HashMap<String, Date> files, String type, String backupLocation) {
        for (HashMap.Entry<String, Date> file : files.entrySet()) {
            HashMap<String, Object> fileListItem = new HashMap<>();

            fileListItem.put("filePath", file.getKey());
            fileListItem.put("lastModified", file.getValue());
            fileListItem.put("type", type);
            fileListItem.put("location", backupLocation);

            fileList.add(fileListItem);
        }
    }
}
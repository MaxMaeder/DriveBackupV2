package ratismal.drivebackup.handler;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitScheduler;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.onedrive.OneDriveUploader;
import ratismal.drivebackup.util.MessageUtil;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class CommandHandler implements CommandExecutor {

    private DriveBackup plugin;

    /**
     * CommandHandler constructor
     *
     * @param plugin DriveBackup plugin
     */
    public CommandHandler(DriveBackup plugin) {
        this.plugin = plugin;
    }

    /**
     * Command executor
     *
     * @param sender Player who sent command
     * @param cmd    Command that was sent
     * @param label  Command alias that was used
     * @param args   Arguments that followed command
     * @return true if successful
     */
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("drivebackup")) {
            if (args.length > 0) {
                switch (args[0].toLowerCase()) {
                    case "v":
                        version(sender);
                        break;
                    case "reloadconfig":
                        if (hasPerm(sender, "drivebackup.reloadConfig")) {
                            reloadConfig(sender);
                        }
                        break;
                    case "linkaccount":
                    	if (hasPerm(sender, "drivebackup.linkAccounts")) {
                    		switch (args[1].toLowerCase()) {
                                case "googledrive":
                                    try {
                                        GoogleDriveUploader.authenticateUser(plugin, sender);
                                    } catch (Exception e) {
                                        MessageUtil.sendMessage(sender, "Failed to link your Google Drive account");
                                    
                                        MessageUtil.sendConsoleException(e);
                                    }
                                    break;
                      		    case "onedrive":
                      			    try {
                      				    OneDriveUploader.authenticateUser(plugin, sender);
                      			    } catch (Exception e) {
                      				    MessageUtil.sendMessage(sender, "Failed to link your OneDrive account");
                      				
                      				    MessageUtil.sendConsoleException(e);
                      			    }
                      			    break;
                                default:
                                    help(sender);
                                    break;
                    		}
                    	}
                    	break;
                    case "backup":
                        if (hasPerm(sender, "drivebackup.backup")) {
                            //if (GoogleUploader.isGoodToGo()) {
                            MessageUtil.sendMessage(sender, "Forcing a backup");
                            Runnable t = new UploadThread(true);
                            new Thread(t).start();
                            //MessageUtil.sendMessage(sender, "This command has been deprecated.");
                        }
                        break;
                    default:
                        help(sender);
                        break;
                }
                return true;
            } else {
                help(sender);
                return true;
            }
        }
        
        return false;
    }

    /**
     * Check if sender has permissions to do command
     *
     * @param sender player who did command
     * @param perm   permission to check
     * @return true if they have permission
     */
    boolean hasPerm(CommandSender sender, String perm) {
        if (!sender.hasPermission(perm)) {
            noPerms(sender);
            return false;
        }
        return true;
    }

    /**
     * Sends help message to player
     *
     * @param sender player
     */
    void help(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "|=======" + ChatColor.DARK_RED + "DriveBackup" + ChatColor.GOLD + "=======|");
        sender.sendMessage(ChatColor.GOLD + "/drivebackup" + ChatColor.DARK_AQUA + " - Display this menu");
        sender.sendMessage(ChatColor.GOLD + "/drivebackup v" + ChatColor.DARK_AQUA + " - Displays plugin version");
        sender.sendMessage(ChatColor.GOLD + "/drivebackup linkaccount googledrive" + ChatColor.DARK_AQUA + " - Links your Google Drive account for backups");
        sender.sendMessage(ChatColor.GOLD + "/drivebackup linkaccount onedrive" + ChatColor.DARK_AQUA + " - Links your OneDrive account for backups");
        sender.sendMessage(ChatColor.GOLD + "/drivebackup reloadconfig" + ChatColor.DARK_AQUA + " - Reloads config.yml");
        sender.sendMessage(ChatColor.GOLD + "/drivebackup backup" + ChatColor.DARK_AQUA + " - Manually initiate a backup");
    }

    /**
     * Tells player plugin version
     *
     * @param sender player
     */
    void version(CommandSender sender) {
    	MessageUtil.sendMessage(sender, "Currently running on version " + plugin.getDescription().getVersion());
    }

    /**
     * Tells the player they don't have permissions to do a command
     *
     * @param sender player
     */
    public void noPerms(CommandSender sender) {
        String noPerms = Config.getNoPerms();
        noPerms = MessageUtil.processGeneral(noPerms);
        MessageUtil.sendMessage(sender, noPerms);
    }

    /**
     * Reloads the configs, and tells the player it has been reloaded
     *
     * @param sender player
     */
    public void reloadConfig(CommandSender sender) {
        DriveBackup.reloadLocalConfig();
        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.cancelTasks(DriveBackup.getInstance());
        DriveBackup.startThread();
        MessageUtil.sendMessage(sender, "Config reloaded!");
    }

}

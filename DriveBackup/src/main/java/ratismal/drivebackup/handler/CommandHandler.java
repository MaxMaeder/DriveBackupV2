package ratismal.drivebackup.handler;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitScheduler;
import ratismal.drivebackup.DownloadThread;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.config.Config;
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
                    case "backup":
                        if (hasPerm(sender, "drivebackup.backup")) {
                            //if (GoogleUploader.isGoodToGo()) {
                            MessageUtil.sendMessage(sender, "Forcing a backup.");
                            Runnable t = new UploadThread(true);
                            new Thread(t).start();
                            //MessageUtil.sendMessage(sender, "This command has been deprecated.");
                        }
                        break;
                    case "download":
                        Runnable t = new DownloadThread(args[1], args[2], args[3]);
                        new Thread(t).start();
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
        sender.sendMessage(ChatColor.GOLD + "|=======DriveBackup=======|");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/drivebackup" + ChatColor.GOLD + " - Display this menu");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/drivebackup v" + ChatColor.GOLD + " - Displays plugin version");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/drivebackup reloadconfig" + ChatColor.GOLD + " - Reload configs");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/drivebackup backup" + ChatColor.GOLD + " - Backups the latest backup");
    }

    /**
     * Tells player plugin version
     *
     * @param sender player
     */
    void version(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "DriveBackup" + ChatColor.LIGHT_PURPLE + " is running on version " + plugin.getDescription().getVersion());
    }

    /**
     * Tells the player they don't have permissions to do a command
     *
     * @param sender player
     */
    public void noPerms(CommandSender sender) {
        String noPerms = Config.getNoPerms();
        noPerms = MessageUtil.processGeneral(noPerms);
        sender.sendMessage(noPerms);
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
        sender.sendMessage(ChatColor.GOLD + "Configs reloaded!");
    }

}

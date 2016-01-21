package ratismal.drivebackup.handler;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.net.UploadThread;
import ratismal.drivebackup.net.Uploader;
import ratismal.drivebackup.util.FileUtil;
import ratismal.drivebackup.util.MessageUtil;

import java.io.File;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class CommandHandler implements CommandExecutor {

    private DriveBackup plugin;
    private Config config;

    /**
     * CommandHandler constructor
     *
     * @param plugin MoneyThief plugin
     * @param config config
     */
    public CommandHandler(DriveBackup plugin, Config config) {
        this.plugin = plugin;
        this.config = config;
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
                            //if (Uploader.isGoodToGo()) {
                            MessageUtil.sendMessageToAllPlayers("Backing up backup, server may lag for a little while...");
                            Runnable t = new UploadThread(sender);
                            new Thread(t).start();
                        }
                        break;
                    case "list":
                        if (hasPerm(sender, "drivebackup.list")) {
                            FileUtil.getFileToUpload(sender, true);
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
        sender.sendMessage(ChatColor.GOLD + "|=======DriveBackup=======|");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/drivebackup" + ChatColor.GOLD + " - Display this menu");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/drivebackup reloadconfig" + ChatColor.GOLD + " - Reload configs");
        sender.sendMessage(ChatColor.LIGHT_PURPLE + "/drivebackup backup" + ChatColor.GOLD + " - Backups the latest backup");
    }

    /**
     * Tells player plugin version
     *
     * @param sender player
     */
    void version(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "MoneyThief" + ChatColor.LIGHT_PURPLE + " is running on version " + plugin.getDescription().getVersion());
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
        config.reload();
        sender.sendMessage(ChatColor.GOLD + "Configs reloaded!");
    }

}

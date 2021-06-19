package ratismal.drivebackup.handler.commandHandler;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;

import ratismal.drivebackup.TestThread;
import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.uploaders.dropbox.DropboxUploader;
import ratismal.drivebackup.uploaders.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.uploaders.onedrive.OneDriveUploader;
import ratismal.drivebackup.config.Permissions;
import ratismal.drivebackup.handler.DebugCollector;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.util.MessageUtil;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class CommandHandler implements CommandExecutor {
    private static final String CHAT_KEYWORD = "drivebackup";

    private DriveBackup plugin;

    /**
     * Creates an instance of the {@code CommandHandler} object
     * @param plugin a reference to the plugin
     */
    public CommandHandler(DriveBackup plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles commands sent by players
     * @param sender the player who sent command
     * @param command  the command that was sent
     * @param label the command alias that was used
     * @param args any arguments that followed the command
     * @return whether the command was handled
     */
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player || sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender)) {
            MessageUtil.sendMessage(sender, "DriveBackupV2 only supports commands sent in-game and via the console");
            return true;
        }
        if (!command.getName().equalsIgnoreCase(CHAT_KEYWORD)) {
            return false;
        } 
        if (args.length == 0) {
            BasicCommands.sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                BasicCommands.sendDocs(sender);
                break;
            case "v":
                BasicCommands.sendVersion(sender);
                break;
            case "reloadconfig":
                if (!Permissions.hasPerm(sender, Permissions.RELOAD_CONFIG)) break;

                DriveBackup.reloadLocalConfig();
                MessageUtil.sendMessage(sender, "Config reloaded!");

                break;
            case "debug":
                if (!Permissions.hasPerm(sender, Permissions.RELOAD_CONFIG)) break;

                MessageUtil.sendMessage(sender, "Generating Debug Log");

                DebugCollector debugInfo = new DebugCollector(this.plugin);
                String publishedUrl = debugInfo.publish(this.plugin);
                MessageUtil.sendMessage(sender, "Debug URL: " + publishedUrl);

                break;
            case "linkaccount":
                if (args.length < 2) {
                    BasicCommands.sendHelp(sender);
                    break;
                } 
                
                if (!Permissions.hasPerm(sender, Permissions.LINK_ACCOUNTS)) break;

                switch (args[1].toLowerCase()) {
                    case "googledrive":
                        GoogleDriveUploader.authenticateUser(plugin, sender);
                        break;
                    case "onedrive":
                        try {
                            OneDriveUploader.authenticateUser(plugin, sender);
                        } catch (Exception e) {
                            MessageUtil.sendMessage(sender, "Failed to link your OneDrive account");
                        
                            MessageUtil.sendConsoleException(e);
                        }
                        break;
                    case "dropbox":
                        try {
                            DropboxUploader.authenticateUser(plugin, sender);
                        } catch (Exception e) {
                            MessageUtil.sendMessage(sender, "Failed to link your Dropbox account");

                            MessageUtil.sendConsoleException(e);
                        }
                        break;
                    default:
                        BasicCommands.sendHelp(sender);
                        break;
                    }
                break;
            case "status":
                if (!Permissions.hasPerm(sender, Permissions.GET_BACKUP_STATUS)) break;
                
                MessageUtil.sendMessage(sender, UploadThread.getBackupStatus());

                break;
            case "nextbackup":
                if (!Permissions.hasPerm(sender, Permissions.GET_NEXT_BACKUP)) break;

                MessageUtil.sendMessage(sender, UploadThread.getNextAutoBackup());
                

                break;
            case "backup":
                if (!Permissions.hasPerm(sender, Permissions.BACKUP)) break;

                MessageUtil.sendMessage(sender, "Forcing a backup");

                Runnable uploadThread = new UploadThread(sender);
                new Thread(uploadThread).start();
                
                break;
            case "test":
                if (!Permissions.hasPerm(sender, Permissions.BACKUP)) break;

                Runnable testThread = new TestThread(sender, args);
                new Thread(testThread).start();
                    
                break;
            default:
                BasicCommands.sendHelp(sender);
                break;
        }

        return true;
    }
}

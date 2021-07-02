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
    public static final String CHAT_KEYWORD = "drivebackup";

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
            new MessageUtil("DriveBackupV2 only supports commands sent in-game and via the console").to(sender).toConsole(false).send();
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
                new MessageUtil("Config reloaded!").to(sender).toConsole(false).send();

                break;
            case "debug":
                if (!Permissions.hasPerm(sender, Permissions.RELOAD_CONFIG)) break;

                new MessageUtil("Generating Debug Log").to(sender).toConsole(false).send();

                DebugCollector debugInfo = new DebugCollector(this.plugin);
                String publishedUrl = debugInfo.publish(this.plugin);
                new MessageUtil("Debug URL: " + publishedUrl).to(sender).toConsole(false).send();

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
                            new MessageUtil("Failed to link your OneDrive account").to(sender).toConsole(false).send();
                        
                            MessageUtil.sendConsoleException(e);
                        }
                        break;
                    case "dropbox":
                        try {
                            DropboxUploader.authenticateUser(plugin, sender);
                        } catch (Exception e) {
                            new MessageUtil("Failed to link your Dropbox account").to(sender).toConsole(false).send();

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
                
                new MessageUtil(UploadThread.getBackupStatus()).to(sender).toConsole(false).send();

                break;
            case "nextbackup":
                if (!Permissions.hasPerm(sender, Permissions.GET_NEXT_BACKUP)) break;

                new MessageUtil(UploadThread.getNextAutoBackup()).to(sender).toConsole(false).send();
                

                break;
            case "backup":
                if (!Permissions.hasPerm(sender, Permissions.BACKUP)) break;

                new MessageUtil("Forcing a backup").to(sender).toConsole(false).send();

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

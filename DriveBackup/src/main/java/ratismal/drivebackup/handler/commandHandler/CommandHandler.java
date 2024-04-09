package ratismal.drivebackup.handler.commandHandler;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.TestThread;
import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.config.PermissionHandler;
import ratismal.drivebackup.constants.Permission;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.uploaders.AuthenticationProvider;
import ratismal.drivebackup.uploaders.Authenticator;
import ratismal.drivebackup.util.MessageUtil;

import java.util.Locale;

import static ratismal.drivebackup.config.Localization.intl;

/**
 * Created by Ratismal on 2016-01-20.
 */
@Deprecated
public class CommandHandler implements CommandExecutor {
    public static final String CHAT_KEYWORD = "drivebackup";

    /**
     * Handles commands sent by players
     * @param sender the player who sent command
     * @param command  the command that was sent
     * @param label the command alias that was used
     * @param args any arguments that followed the command
     * @return true if the command was handled, false otherwise
     */
    public boolean onCommand(CommandSender sender, @NotNull Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase(CHAT_KEYWORD)) {
            return false;
        } 
        if (args.length == 0) {
            BasicCommands.sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help":
                BasicCommands.sendDocs(sender);
                break;
            case "commands":
                BasicCommands.sendHelp(sender);
                break;
            case "version":
            case "v":
                BasicCommands.sendVersion(sender);
                break;
            case "reloadconfig":
                if (!PermissionHandler.hasPerm(sender, Permission.RELOAD_CONFIG)) {
                    break;
                }
                DriveBackup.reloadLocalConfig();
                MessageUtil.Builder().mmText(intl("config-reloaded")).to(sender).send();
                break;
            /*case "debug":
                if (!PermissionHandler.hasPerm(sender, PermissionHandler.RELOAD_CONFIG)) break;
                MessageUtil.Builder().mmText(intl("debug-log-creating")).to(sender).toConsole(false).send();
                DebugCollector debugInfo = new DebugCollector(DriveBackup.getInstance());
                String publishedUrl = debugInfo.publish(DriveBackup.getInstance());
                MessageUtil.Builder()
                    .mmText(intl("debug-log-created"), "url", publishedUrl)
                    .to(sender).toConsole(false)
                    .send();
                break;*/
            case "linkaccount":
            case "link":
                if (args.length < 2) {
                    BasicCommands.sendHelp(sender);
                    break;
                }
                if (!PermissionHandler.hasPerm(sender, Permission.LINK_ACCOUNTS)) {
                    break;
                }
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "googledrive":
                        Authenticator.authenticateUser(AuthenticationProvider.GOOGLE_DRIVE, sender);
                        break;
                    case "onedrive":
                        Authenticator.authenticateUser(AuthenticationProvider.ONEDRIVE, sender);
                        break;
                    case "dropbox":
                        Authenticator.authenticateUser(AuthenticationProvider.DROPBOX, sender);
                        break;
                    default:
                        BasicCommands.sendHelp(sender);
                        break;
                    }
                break;
            case "unlinkaccount":
            case "unlink":
                if (args.length < 2) {
                    BasicCommands.sendHelp(sender);
                    break;
                }
                if (!PermissionHandler.hasPerm(sender, Permission.LINK_ACCOUNTS)) {
                    break;
                }
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "googledrive":
                        Authenticator.unAuthenticateUser(AuthenticationProvider.GOOGLE_DRIVE, sender);
                        break;
                    case "onedrive":
                        Authenticator.unAuthenticateUser(AuthenticationProvider.ONEDRIVE, sender);
                        break;
                    case "dropbox":
                        Authenticator.unAuthenticateUser(AuthenticationProvider.DROPBOX, sender);
                        break;
                    default:
                        BasicCommands.sendHelp(sender);
                        break;
                }
                break;
            case "status":
                if (!PermissionHandler.hasPerm(sender, Permission.GET_BACKUP_STATUS)) {
                    break;
                }
                MessageUtil.Builder().mmText(UploadThread.getBackupStatus()).to(sender).toConsole(false).send();
                break;
            case "nextbackup":
                if (!PermissionHandler.hasPerm(sender, Permission.GET_NEXT_BACKUP)) {
                    break;
                }
                MessageUtil.Builder().mmText(UploadThread.getNextAutoBackup()).to(sender).toConsole(false).send();
                break;
            case "backup":
                if (!PermissionHandler.hasPerm(sender, Permission.BACKUP)) {
                    break;
                }
                MessageUtil.Builder().mmText(intl("backup-forced")).to(sender).send();
                Runnable uploadThread = new UploadThread(sender);
                new Thread(uploadThread).start();
                break;
            case "test":
                if (!PermissionHandler.hasPerm(sender, Permission.BACKUP)) {
                    break;
                }
                Runnable testThread = new TestThread(sender, args);
                new Thread(testThread).start();
                break;
            case "update":
                if (!PermissionHandler.hasPerm(sender, Permission.BACKUP)) {
                    break;
                }
                DriveBackup.updater.runUpdater(sender);
                break;
            default:
                BasicCommands.sendHelp(sender);
                break;
        }
        return true;
    }
}

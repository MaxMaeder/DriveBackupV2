package ratismal.drivebackup.platforms.bukkit.commandHandler;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.ConfigurateException;
import ratismal.drivebackup.TestThread;
import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.constants.Initiator;
import ratismal.drivebackup.constants.Permission;
import ratismal.drivebackup.handler.BackupStatus;
import ratismal.drivebackup.handler.command.BasicCommands;
import ratismal.drivebackup.handler.messages.MessageHandler;
import ratismal.drivebackup.objects.Player;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.platforms.bukkit.BukkitPlugin;
import ratismal.drivebackup.uploaders.AuthenticationProvider;
import ratismal.drivebackup.uploaders.Authenticator;

import java.util.Locale;

import static ratismal.drivebackup.handler.command.BasicCommands.send;

/**
 * Created by Ratismal on 2016-01-20.
 */
public class CommandHandler implements CommandExecutor {
    public static final String CHAT_KEYWORD = "drivebackup";
    
    private final DriveBackupInstance instance;
    
    public CommandHandler(DriveBackupInstance instance) {
        this.instance = instance;
    }

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
            BasicCommands.sendHelp(instance, sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help":
            case "h":
                BasicCommands.sendDocs(instance, sender);
                break;
            case "commands":
                BasicCommands.sendHelp(instance, sender);
                break;
            case "version":
            case "ver":
            case "v":
                BasicCommands.sendVersion(instance, sender);
                break;
            case "reloadconfig":
                if (!sender.hasPermission(Permission.RELOAD_CONFIG.getPermission())) {
                    BasicCommands.sendNoPerms(instance, sender);
                    break;
                }
                try {
                    instance.getConfigHandler().reload();
                    instance.getLangConfigHandler().reload();
                } catch (ConfigurateException e) {
                    instance.getLoggingHandler().error("Failed to reload config by command", e);
                }
                MessageHandler handler = instance.getMessageHandler().Builder().getLang("config-reloaded");
                send(handler, sender);
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
                if (!sender.hasPermission(Permission.LINK_ACCOUNTS.getPermission())) {
                    BasicCommands.sendNoPerms(instance, sender);
                    break;
                }
                if (args.length < 2) {
                    BasicCommands.sendHelp(instance, sender);
                    break;
                }
                Player player = new Player(((org.bukkit.entity.Player) sender).getUniqueId(), sender.getName());
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "googledrive":
                        Authenticator.authenticateUser(AuthenticationProvider.GOOGLE_DRIVE, player, instance);
                        break;
                    case "onedrive":
                        Authenticator.authenticateUser(AuthenticationProvider.ONEDRIVE, player, instance);
                        break;
                    case "dropbox":
                        Authenticator.authenticateUser(AuthenticationProvider.DROPBOX, player, instance);
                        break;
                    default:
                        BasicCommands.sendHelp(instance, sender);
                        break;
                    }
                break;
            case "unlinkaccount":
            case "unlink":
                if (!sender.hasPermission(Permission.LINK_ACCOUNTS.getPermission())) {
                    BasicCommands.sendNoPerms(instance, sender);
                    break;
                }
                if (args.length < 2) {
                    BasicCommands.sendHelp(instance, sender);
                    break;
                }
                Player player2 = new Player(((org.bukkit.entity.Player) sender).getUniqueId(), sender.getName());
                switch (args[1].toLowerCase(Locale.ROOT)) {
                    case "googledrive":
                        Authenticator.unAuthenticateUser(AuthenticationProvider.GOOGLE_DRIVE, player2, BukkitPlugin.getInstance());
                        break;
                    case "onedrive":
                        Authenticator.unAuthenticateUser(AuthenticationProvider.ONEDRIVE, player2, BukkitPlugin.getInstance());
                        break;
                    case "dropbox":
                        Authenticator.unAuthenticateUser(AuthenticationProvider.DROPBOX, player2, BukkitPlugin.getInstance());
                        break;
                    default:
                        BasicCommands.sendHelp(instance, sender);
                        break;
                }
                break;
            case "status":
                if (!sender.hasPermission(Permission.GET_BACKUP_STATUS.getPermission())) {
                    BasicCommands.sendNoPerms(instance, sender);
                    break;
                }
                MessageHandler handler2 = instance.getMessageHandler().Builder().miniMessage(BackupStatus.getStatus().toString());
                send(handler2, sender);
                break;
            case "nextbackup":
                if (!sender.hasPermission(Permission.GET_NEXT_BACKUP.getPermission())) {
                    BasicCommands.sendNoPerms(instance, sender);
                    break;
                }
                //MessageUtil.Builder().mmText(UploadThread.getNextAutoBackup()).to(sender).toConsole(false).send();
                break;
            case "backup":
                if (!sender.hasPermission(Permission.BACKUP.getPermission())) {
                    BasicCommands.sendNoPerms(instance, sender);
                    break;
                }
                MessageHandler handler3 = instance.getMessageHandler().Builder().getLang("backup-forced");
                send(handler3, sender);
                Runnable uploadThread = new UploadThread(instance, Initiator.CONSOLE);
                new Thread(uploadThread).start();
                break;
            case "test":
                if (!sender.hasPermission(Permission.BACKUP.getPermission())) {
                    BasicCommands.sendNoPerms(instance, sender);
                    break;
                }
                Runnable testThread = new TestThread(BukkitPlugin.getInstance(),
                        new Player(((org.bukkit.entity.Player)sender).getUniqueId(), null), args);
                new Thread(testThread).start();
                break;
            case "update":
                if (!sender.hasPermission(Permission.BACKUP.getPermission())) {
                    BasicCommands.sendNoPerms(instance, sender);
                    break;
                }
                //DriveBackup.updater.runUpdater(sender);
                break;
            default:
                BasicCommands.sendHelp(instance, sender);
                break;
        }
        return true;
    }
}

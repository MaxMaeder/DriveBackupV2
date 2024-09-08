package ratismal.drivebackup.handler.command;

import org.jetbrains.annotations.Contract;
import org.spongepowered.configurate.ConfigurateException;
import ratismal.drivebackup.TestThread;
import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.constants.Permission;
import ratismal.drivebackup.handler.BackupStatus;
import ratismal.drivebackup.handler.logging.PrefixedLogger;
import ratismal.drivebackup.handler.messages.MessageHandler;
import ratismal.drivebackup.objects.Command;
import ratismal.drivebackup.objects.Player;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.uploaders.AuthenticationProvider;
import ratismal.drivebackup.uploaders.Authenticator;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class CommandHandler {
    
    @Contract (value = " -> fail", pure = true)
    private CommandHandler() {
        throw new IllegalStateException("Utility class");
    }
    
    private static boolean hasPerm(DriveBackupInstance instance, Player player, Permission permission) {
        return instance.getPermissionHandler().hasPermission(player, permission);
    }
    
    public static void handleCommand(DriveBackupInstance instance, Command command) {
        String baseCommand = command.getBaseCommand().toLowerCase(Locale.ROOT);
        MessageHandler messageHandler = instance.getMessageHandler().Builder();
        String header = messageHandler.getLangString("drivebackup-command-header");
        PrefixedLogger logger = instance.getLoggingHandler().getPrefixedLogger("CommandHandler");
        Player player = command.getPlayer();
        switch (baseCommand) {
            case "help":
                messageHandler.notToConsole().getLang("drivebackup-docs-command", "header", header).to(player).send();
                return;
            case "commands":
                messageHandler.notToConsole().getLang("drivebackup-help-command", "header", header).to(player).send();
                return;
            case "version":
            case "v":
                Map<String, String> placeholders = new HashMap<>(5);
                placeholders.put("header", header);
                placeholders.put("plugin-version", instance.getCurrentVersion().toString());
                placeholders.put("java-version", System.getProperty("java.version"));
                placeholders.put("server-software", instance.getServerInfo().getServerType());
                placeholders.put("server-version", instance.getServerInfo().getServerVersion());
                //TODO update checker add msg if update is available
                messageHandler.notToConsole().getLang("drivebackup-version-command", placeholders).to(player).send();
                return;
            case "reloadconfig":
                if (!hasPerm(instance, player, Permission.RELOAD_CONFIG)) {
                    sendNoPerms(instance, player);
                    return;
                }
                try {
                    instance.getConfigHandler().reload();
                    instance.getLangConfigHandler().reload();
                    messageHandler.getLang("config-reloaded").to(player).send();
                } catch (ConfigurateException e) {
                    messageHandler.getLang("config-reload-fail").to(player).send();
                    logger.error("Failed to reload config", e);
                }
                return;
            case "linkaccount":
            case "link":
                handleLink(instance, command);
                return;
            case "unlinkaccount":
            case "unlink":
                handleUnlink(instance, command);
                return;
            case "status":
                if (!hasPerm(instance, player, Permission.GET_BACKUP_STATUS)) {
                    sendNoPerms(instance, player);
                    return;
                }
                //TODO improve status message
                messageHandler.notToConsole().text(BackupStatus.getStatus().toString()).to(player).send();
                break;
            case "nextbackup":
                if (!hasPerm(instance, player, Permission.GET_NEXT_BACKUP)) {
                    sendNoPerms(instance, player);
                    return;
                }
                messageHandler.notToConsole().miniMessage(UploadThread.getNextAutoBackup(instance)).to(player).send();
                break;
            case "backup":
                if (!hasPerm(instance, player, Permission.BACKUP)) {
                    sendNoPerms(instance, player);
                    return;
                }
                messageHandler.notToConsole().getLang("backup-forced").to(player).send();
                Runnable uploadThread = new UploadThread(instance, player);
                new Thread(uploadThread).start();
                return;
            case "test":
                if (!hasPerm(instance, player, Permission.BACKUP)) {
                    sendNoPerms(instance, player);
                    return;
                }
                String[] fullArgs = new String[command.getArgs().length + 1];
                fullArgs[0] = command.getSubCommand();
                System.arraycopy(command.getArgs(), 0, fullArgs, 1, command.getArgs().length);
                Runnable testThread = new TestThread(instance, player, fullArgs);
                new Thread(testThread).start();
                break;
            case "update":
                //TODO
                break;
            default:
                sendHelp(instance, player);
                break;
        }
    }
    
    private static void sendHelp(DriveBackupInstance instance, Player player) {
        instance.getMessageHandler().Builder().notToConsole().getLang("drivebackup-help-command").to(player).send();
    }
    
    private static void sendNoPerms(DriveBackupInstance instance, Player player) {
        instance.getMessageHandler().Builder().notToConsole().getLang("no-perm").to(player).send();
    }
    
    private static void handleLink(DriveBackupInstance instance, Command command) {
        Player player = command.getPlayer();
        String subCommand = command.getSubCommand();
        if (subCommand == null) {
            sendHelp(instance, player);
            return;
        }
        if (!hasPerm(instance, player, Permission.LINK_ACCOUNTS)) {
            sendNoPerms(instance, player);
            return;
        }
        switch (subCommand.toLowerCase(Locale.ROOT)) {
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
                sendHelp(instance, player);
                break;
        }
    }
    
    private static void handleUnlink(DriveBackupInstance instance, Command command) {
        Player player = command.getPlayer();
        String subCommand = command.getSubCommand();
        if (subCommand == null) {
            sendHelp(instance, player);
            return;
        }
        if (!hasPerm(instance, player, Permission.LINK_ACCOUNTS)) {
            sendNoPerms(instance, player);
            return;
        }
        switch (subCommand.toLowerCase(Locale.ROOT)) {
            case "googledrive":
                Authenticator.unAuthenticateUser(AuthenticationProvider.GOOGLE_DRIVE, player, instance);
                break;
            case "onedrive":
                Authenticator.unAuthenticateUser(AuthenticationProvider.ONEDRIVE, player, instance);
                break;
            case "dropbox":
                Authenticator.unAuthenticateUser(AuthenticationProvider.DROPBOX, player, instance);
                break;
            default:
                sendHelp(instance, player);
                break;
        }
    }
    
}

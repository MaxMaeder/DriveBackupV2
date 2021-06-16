package ratismal.drivebackup.config;

import org.bukkit.command.CommandSender;

import ratismal.drivebackup.handler.commandHandler.BasicCommands;

public class Permissions {
    public static final String LINK_ACCOUNTS = "drivebackup.linkAccounts";
    public static final String RELOAD_CONFIG = "drivebackup.reloadConfig";
    public static final String GET_NEXT_BACKUP = "drivebackup.getNextBackup";
    public static final String GET_BACKUP_STATUS = "drivebackup.getBackupStatus";
    public static final String BACKUP = "drivebackup.backup";

    /**
     * Checks if the specified player has the specified permission
     * @param player the player
     * @param permission the permission
     * @return whether they have permissions
     */
    public static boolean hasPerm(CommandSender player, String permission) {
        if (!player.hasPermission(permission)) {
            BasicCommands.sendNoPerms(player);
            return false;
        }
        return true;
    }
}

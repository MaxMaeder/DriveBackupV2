package ratismal.drivebackup.config;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

    /**
     * Returns a list of players with the specified permission
     * @param permission the permission, as a {@code String}
     * @return the list of players
     */
    public static List<CommandSender> getPlayersWithPerm(String permission) {
        ArrayList<CommandSender> players = new ArrayList<>();

        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (player.hasPermission(permission)) {
                players.add(player);
            }
        }

        return players;
    }
}

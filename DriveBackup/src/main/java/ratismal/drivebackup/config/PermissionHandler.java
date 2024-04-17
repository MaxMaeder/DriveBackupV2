package ratismal.drivebackup.config;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.constants.Permission;
import ratismal.drivebackup.handler.commandHandler.BasicCommands;

public final class PermissionHandler {
    
    private PermissionHandler() {
        throw new IllegalStateException("Utility class");
    }
    
    /**
     * Checks if the specified player has the specified permission
     * @param player the player
     * @param permission the permission
     * @return whether they have permissions
     */
    public static boolean hasPerm(@NotNull CommandSender player, Permission permission) {
        return player.hasPermission(permission.getPermission());
    }

    /**
     * Returns a list of players with the specified permission
     * @param permission the permission, as a {@code String}
     * @return the list of players
     */
    @NotNull
    public static List<CommandSender> getPlayersWithPerm(Permission permission) {
        ArrayList<CommandSender> players = new ArrayList<>();
        for (Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (player.hasPermission(permission.getPermission())) {
                players.add(player);
            }
        }
        return players;
    }
}

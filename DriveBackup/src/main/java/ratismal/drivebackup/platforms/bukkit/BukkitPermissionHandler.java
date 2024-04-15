package ratismal.drivebackup.platforms.bukkit;

import org.bukkit.Server;
import org.jetbrains.annotations.Contract;
import ratismal.drivebackup.constants.Permission;
import ratismal.drivebackup.handler.permission.PermissionHandler;
import ratismal.drivebackup.objects.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BukkitPermissionHandler implements PermissionHandler {
    
    private static final String PERMISSION_CANNOT_BE_NULL = "Permission cannot be null";
    private static final String PLAYER_CANNOT_BE_NULL = "Player cannot be null";
    
    private final Server server;
    
    @Contract (pure = true)
    public BukkitPermissionHandler(Server server) {
        this.server = server;
    }
    
    @Override
    public boolean hasPermission(Player player, Permission permission) {
        if (player == null) {
            throw new IllegalArgumentException(PLAYER_CANNOT_BE_NULL);
        }
        if (permission == null) {
            throw new IllegalArgumentException(PERMISSION_CANNOT_BE_NULL);
        }
        UUID uuid = player.getUuid();
        if (server.getPlayer(uuid) != null) {
            return server.getPlayer(uuid).hasPermission(permission.getPermission());
        }
        return false;
    }
    
    @Override
    public List<Player> getPlayersWithPermission(Permission permission) {
        if (permission == null) {
            throw new IllegalArgumentException(PERMISSION_CANNOT_BE_NULL);
        }
        List<Player> players = new ArrayList<>(10);
        for (org.bukkit.entity.Player bukkitPlayer : server.getOnlinePlayers()) {
            Player player = new Player(bukkitPlayer.getName(), bukkitPlayer.getUniqueId());
            if (hasPermission(player, permission)) {
                players.add(player);
            }
        }
        return players;
    }
}

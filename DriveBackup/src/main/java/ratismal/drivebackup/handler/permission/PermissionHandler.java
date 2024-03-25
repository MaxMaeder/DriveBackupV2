package ratismal.drivebackup.handler.permission;

import ratismal.drivebackup.constants.Permission;
import ratismal.drivebackup.objects.Player;

import java.util.List;

public interface PermissionHandler {
    
    boolean hasPermission(Player player, Permission permission);
    List<Player> getPlayersWithPermission(Permission permission);
}

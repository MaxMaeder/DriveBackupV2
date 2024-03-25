package ratismal.drivebackup.platforms.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class BukkitUtils {
    
    private BukkitUtils() {
    }
    
    public static Player getPlayer(ratismal.drivebackup.objects.Player player) {
        return Bukkit.getPlayer(player.getUuid());
    }
}

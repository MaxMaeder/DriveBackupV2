package ratismal.drivebackup.platforms.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class BukkitUtils {
    
    @Contract (pure = true)
    private BukkitUtils() {
    }
    
    public static Player getPlayer(@NotNull ratismal.drivebackup.objects.Player player) {
        return Bukkit.getPlayer(player.getUuid());
    }
    
}

package ratismal.drivebackup.platforms.bukkit;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.handler.player.PlayerHandler;
import ratismal.drivebackup.objects.Player;

import java.util.Collection;

public final class BukkitPlayerHandler implements PlayerHandler {
    
    private final BukkitPlugin instance;
    
    @Contract (pure = true)
    public BukkitPlayerHandler(BukkitPlugin instance) {
        this.instance = instance;
    }
    
    @Override
    public void sendMessage(Player player, String message) {
        BukkitUtils.getPlayer(player).sendMessage(message);
    }
    
    @Override
    public void sendMessage(@NotNull Collection<Player> players, String message) {
        for (Player player : players) {
            sendMessage(player, message);
        }
    }
}

package ratismal.drivebackup.platforms.bukkit;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.handler.messages.MessageHandler;
import ratismal.drivebackup.objects.Player;

import java.util.Collection;

public final class BukkitMessageHandler extends MessageHandler {
    
    private final BukkitPlugin plugin;
    
    public BukkitMessageHandler(BukkitPlugin instance) {
        super(instance);
        plugin = instance;
    }
    
    @Contract (" -> new")
    public @NotNull MessageHandler Builder() {
        return new BukkitMessageHandler(plugin);
    }
    
    @Contract (" -> this")
    @Override
    public MessageHandler toAll() {
        Collection<? extends org.bukkit.entity.Player> players = plugin.getServer().getOnlinePlayers();
        recipients.ensureCapacity(players.size());
        for (org.bukkit.entity.Player player : players) {
            recipients.add(new Player(player.getName(), player.getUniqueId()));
        }
        return this;
    }
    
    @Override
    public void sendPlayer(@NotNull Player player) {
        plugin.getAudiences().player(player.getUuid()).sendMessage(getMessage());
    }
}

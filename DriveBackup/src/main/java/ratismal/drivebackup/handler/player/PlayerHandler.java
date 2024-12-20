package ratismal.drivebackup.handler.player;

import ratismal.drivebackup.objects.Player;

import java.util.Collection;
import java.util.List;

public interface PlayerHandler {
    
    List<Player> getOnlinePlayers();
    void sendMessage(Player player, String message);
    void sendMessage(Collection<Player> players, String message);
    
}

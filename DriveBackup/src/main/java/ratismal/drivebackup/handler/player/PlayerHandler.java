package ratismal.drivebackup.handler.player;

import ratismal.drivebackup.objects.Player;

import java.util.Collection;

public interface PlayerHandler {
    
    void sendMessage(Player player, String message);
    
    void sendMessage(Collection<Player> players, String message0);
}

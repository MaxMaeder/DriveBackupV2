package ratismal.drivebackup.objects;

import lombok.Getter;
import org.jetbrains.annotations.Contract;

import java.util.UUID;

@Getter
public final class Player {
    private String name;
    private final UUID uuid;

    @Contract (pure = true)
    public Player(UUID uuid, String name) {
        this.name = name;
        this.uuid = uuid;
    }
    
    @Contract (pure = true)
    public Player(UUID uuid) {
        this.uuid = uuid;
    }
    
}

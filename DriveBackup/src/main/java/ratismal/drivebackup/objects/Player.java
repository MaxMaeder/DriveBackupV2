package ratismal.drivebackup.objects;

import java.util.UUID;

public class Player {
    private String name;
    private final UUID uuid;

    public Player(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }
}

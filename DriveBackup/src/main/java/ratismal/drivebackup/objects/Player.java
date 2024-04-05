package ratismal.drivebackup.objects;

import org.jetbrains.annotations.Contract;

import java.util.UUID;

public final class Player {
    private final String name;
    private final UUID uuid;

    @Contract (pure = true)
    public Player(String name, UUID uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    @Contract (pure = true)
    public String getName() {
        return name;
    }

    @Contract (pure = true)
    public UUID getUuid() {
        return uuid;
    }
}

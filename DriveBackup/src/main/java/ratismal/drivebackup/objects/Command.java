package ratismal.drivebackup.objects;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a command ran by a player
 * BaseCommand is the root command after /drivebackup,
 * subCommand is the sub command,
 * args are the arguments passed with the command,
 * and player is the player who ran the command.
 */
public final class Command {
    
    private final @NotNull String baseCommand;
    private final @Nullable String subCommand;
    private final String[] args;
    private final @NotNull Player player;
    
    @Contract (pure = true)
    public Command(@NotNull String baseCommand, @Nullable String subCommand, @NotNull String[] args, @NotNull Player player) {
        this.baseCommand = baseCommand;
        this.subCommand = subCommand;
        this.args = args.clone();
        this.player = player;
    }
    
    @Contract (pure = true)
    public @NotNull String getBaseCommand() {
        return baseCommand;
    }
    
    @Contract (pure = true)
    public @Nullable String getSubCommand() {
        return subCommand;
    }
    
    public String[] getArgs() {
        return args.clone();
    }
    
    @Contract (pure = true)
    public @NotNull Player getPlayer() {
        return player;
    }
    
}

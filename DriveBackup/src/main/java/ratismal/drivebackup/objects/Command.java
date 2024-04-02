package ratismal.drivebackup.objects;

import org.jetbrains.annotations.Contract;

public final class Command {
    
    private final String baseCommand;
    private final String subCommand;
    private final String[] args;
    
    @Contract (pure = true)
    public Command(String baseCommand, String subCommand, String[] args) {
        this.baseCommand = baseCommand;
        this.subCommand = subCommand;
        this.args = args;
    }
    
    @Contract (pure = true)
    public String getBaseCommand() {
        return baseCommand;
    }
    
    @Contract (pure = true)
    public String getSubCommand() {
        return subCommand;
    }
    
    @Contract (pure = true)
    public String[] getArgs() {
        return args;
    }
}
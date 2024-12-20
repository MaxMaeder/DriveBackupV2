package ratismal.drivebackup.constants;

import org.jetbrains.annotations.Contract;

public enum SubCommands {
    HELP("help", "h"),
    COMMANDS("commands"),
    VERSION("version", "v", "ver"),
    RELOAD_CONFIG("reloadconfig"),
    LINK_ACCOUNT("linkaccount", "link"),
    UNLINK_ACCOUNT("unlinkaccount", "unlink"),
    STATUS("status"),
    NEXT_BACKUP("nextbackup"),
    BACKUP("backup"),
    TEST("test"),
    UPDATE("update");
    
    private final String name;
    private final String[] aliases;
    
    @Contract (pure = true)
    SubCommands(String name, String... aliases) {
        this.name = name;
        this.aliases = aliases;
    }
    
    @Contract (pure = true)
    public String getName() {
        return name;
    }
    
    @Contract (pure = true)
    public String[] getAliases() {
        return aliases;
    }
    
    @Contract (pure = true)
    @Override
    public String toString() {
        return name;
    }
    
    public static SubCommands getSubCommand(String subCommand) {
        for (SubCommands command : values()) {
            if (command.getName().equalsIgnoreCase(subCommand)) {
                return command;
            }
            for (String alias : command.getAliases()) {
                if (alias.equalsIgnoreCase(subCommand)) {
                    return command;
                }
            }
        }
        throw new IllegalArgumentException("Invalid subcommand: " + subCommand);
    }
    
}

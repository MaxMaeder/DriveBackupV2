package ratismal.drivebackup.constants;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public enum Permission {
    
    BACKUP("backup"),
    GET_BACKUP_STATUS("getBackupStatus"),
    GET_NEXT_BACKUP("getNextBackup"),
    RELOAD_CONFIG("reloadConfig"),
    LINK_ACCOUNTS("linkAccounts");
    
    private final String node;
    private static final String PERMISSION_PREFIX = "drivebackup.";
    
    @Contract (pure = true)
    Permission(String permission) {
        node = permission;
    }
    
    @Contract (pure = true)
    public @NotNull String getPermission() {
        return getFullPermission();
    }
    
    @Contract (pure = true)
    public @NotNull String getFullPermission() {
        return PERMISSION_PREFIX + node;
    }
    
    @Contract (pure = true)
    public String getNode() {
        return node;
    }
    
    @Contract (pure = true)
    @Override
    public String toString() {
        return node;
    }
    
}

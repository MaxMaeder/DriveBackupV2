package ratismal.drivebackup.constants;

import org.jetbrains.annotations.Contract;

public enum Permission {
    
    BACKUP("drivebackup.backup"),
    GET_BACKUP_STATUS("drivebackup.getBackupStatus"),
    GET_NEXT_BACKUP("drivebackup.getNextBackup"),
    RELOAD_CONFIG("drivebackup.reloadConfig"),
    LINK_ACCOUNTS("drivebackup.linkAccounts");
    
    private final String permission;
    
    @Contract (pure = true)
    Permission(String permission) {
        this.permission = permission;
    }
    
    @Contract (pure = true)
    public String getPermission() {
        return permission;
    }
    
    @Contract (pure = true)
    @Override
    public String toString() {
        return permission;
    }
    
}

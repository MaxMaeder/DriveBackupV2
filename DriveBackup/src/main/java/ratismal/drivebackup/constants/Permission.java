package ratismal.drivebackup.constants;

public enum Permission {
    
    BACKUP("drivebackup.backup"),
    GET_BACKUP_STATUS("drivebackup.getBackupStatus"),
    GET_NEXT_BACKUP("drivebackup.getNextBackup"),
    RELOAD_CONFIG("drivebackup.reloadConfig"),
    LINK_ACCOUNTS("drivebackup.linkAccounts");
    
    private final String permission;
    
    Permission(String permission) {
        this.permission = permission;
    }
    
    public String getPermission() {
        return permission;
    }
    
    @Override
    public String toString() {
        return permission;
    }
}

package ratismal.drivebackup.constants;

import org.jetbrains.annotations.Contract;

public enum ExternalBackupMethod {
    FTP("FTP"),
    SFTP("SFTP");
    
    private final String name;
    
    @Contract (pure = true)
    ExternalBackupMethod(String name) {
        this.name = name;
    }
    
    @Contract (pure = true)
    public String getName() {
        return name;
    }
    
    @Contract (pure = true)
    @Override
    public String toString() {
        return name;
    }
}

package ratismal.drivebackup.constants;

import lombok.Getter;
import org.jetbrains.annotations.Contract;

@Getter
public enum ExternalBackupMethod {
    FTP("FTP"),
    SFTP("SFTP");
    
    private final String name;
    
    @Contract (pure = true)
    ExternalBackupMethod(String name) {
        this.name = name;
    }
    
    @Contract (pure = true)
    @Override
    public String toString() {
        return name;
    }
    
}

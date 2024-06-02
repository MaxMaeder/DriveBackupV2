package ratismal.drivebackup.handler;

import org.jetbrains.annotations.Contract;
import ratismal.drivebackup.constants.BackupStatusValue;

public class BackupStatus {
    
    private static BackupStatusValue status = BackupStatusValue.NOT_RUNNING;
    
    private BackupStatus() {}
    
    @Contract (pure = true)
    public static BackupStatusValue getStatus() {
        return status;
    }
    
    public static void setStatus(BackupStatusValue status) {
        BackupStatus.status = status;
    }
    
}

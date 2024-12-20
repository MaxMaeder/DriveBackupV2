package ratismal.drivebackup.handler;

import lombok.Getter;
import lombok.Setter;
import ratismal.drivebackup.constants.BackupStatusValue;

public final class BackupStatus {
    
    @Getter
    @Setter
    private static BackupStatusValue status = BackupStatusValue.NOT_RUNNING;
    
    private BackupStatus() {}
    
}

package ratismal.drivebackup.constants;

/**
 * The current status of the backup thread
 */
public enum BackupStatusValue {
    /**
     * The backup thread isn't running
     */
    NOT_RUNNING,
    
    /**
     * The backup thread is compressing the files to be backed up.
     */
    COMPRESSING,
    
    /**
     * The backup thread is uploading the files
     */
    UPLOADING
}

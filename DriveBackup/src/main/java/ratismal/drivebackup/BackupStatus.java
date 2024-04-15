package ratismal.drivebackup;

/**
 * The current status of the backup thread
 */
enum BackupStatus {
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

package ratismal.drivebackup.handler.update;

import org.jetbrains.annotations.Contract;
import ratismal.drivebackup.handler.logging.PrefixedLogger;
import ratismal.drivebackup.platforms.DriveBackupInstance;

public class UpdateTask implements Runnable {
    
    private final DriveBackupInstance instance;
    private final UpdateHandler updateHandler;
    private final PrefixedLogger logger;
    
    @Contract (pure = true)
    public UpdateTask(DriveBackupInstance instance, UpdateHandler updateHandler) {
        this.instance = instance;
        this.updateHandler = updateHandler;
        logger = instance.getLoggingHandler().getPrefixedLogger("UpdateTask");
    }
    
    @Override
    public void run() {
        logger.info("Checking for updates...");
        updateHandler.getLatest();
        if (updateHandler.hasUpdate()) {
            logger.info("New version available: " + updateHandler.getLatestVersion());
            logger.info("Your version: " + updateHandler.getCurrentVersion());
            logger.info("Preforming update...");
            updateHandler.downloadUpdate();
            logger.info("Update complete, new version will be active after restart");
        } else {
            logger.info("No new version available");
        }
    }
}

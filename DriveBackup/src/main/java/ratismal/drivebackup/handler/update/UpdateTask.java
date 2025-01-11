package ratismal.drivebackup.handler.update;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.constants.Permission;
import ratismal.drivebackup.handler.logging.PrefixedLogger;
import ratismal.drivebackup.objects.Player;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.util.Collection;
import java.util.concurrent.ExecutionException;

public final class UpdateTask implements Runnable {
    
    private final DriveBackupInstance instance;
    private final UpdateHandler updateHandler;
    private final PrefixedLogger logger;
    
    @Contract (pure = true)
    public UpdateTask(@NotNull DriveBackupInstance instance, UpdateHandler updateHandler) {
        this.instance = instance;
        this.updateHandler = updateHandler;
        logger = instance.getLoggingHandler().getPrefixedLogger("UpdateTask");
    }
    
    @Override
    public void run() {
        logger.info("Checking for updates...");
        updateHandler.getLatest();
        if (updateHandler.hasUpdate()) {
            StringBuilder sb = new StringBuilder(1000);
            sb.append("DriveBackup has an update available!\n");
            sb.append("New version available: ");
            sb.append(updateHandler.getLatestVersion());
            sb.append("\n");
            sb.append(" (Current version: ");
            sb.append(updateHandler.getCurrentVersion());
            sb.append(")");
            sb.append("\n");
            sb.append("To update automatically, run the command /drivebackup update");
            String message = sb.toString();
            logger.info(message);
            Collection<Player> players = instance.getPermissionHandler().getPlayersWithPermission(Permission.RELOAD_CONFIG);
            try {
                instance.callSyncMethod(() -> {
                    instance.getPlayerHandler().sendMessage(players, message);
                    return Boolean.TRUE;
                }).get();
                logger.info("Sent update message to players");
            } catch (InterruptedException | ExecutionException e) {
                logger.warn("Failed to send update message to players", e);
            }
        } else {
            logger.info("No new version available");
        }
    }
    
}

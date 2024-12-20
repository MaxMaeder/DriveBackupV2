package ratismal.drivebackup.platforms;

import ratismal.drivebackup.api.APIHandler;
import ratismal.drivebackup.configuration.ConfigHandler;
import ratismal.drivebackup.configuration.LangConfigHandler;
import ratismal.drivebackup.handler.debug.ServerInformation;
import ratismal.drivebackup.handler.logging.LoggingHandler;
import ratismal.drivebackup.handler.messages.MessageHandler;
import ratismal.drivebackup.handler.permission.PermissionHandler;
import ratismal.drivebackup.handler.player.PlayerHandler;
import ratismal.drivebackup.handler.task.TaskHandler;
import ratismal.drivebackup.handler.update.UpdateHandler;
import ratismal.drivebackup.objects.Player;
import ratismal.drivebackup.util.Version;

import java.io.File;
import java.util.concurrent.ExecutionException;

public interface DriveBackupInstance {
    
    void addChatInputPlayer(Player player);
    
    void removeChatInputPlayer(Player player);
    boolean isChatInputPlayer(Player player);
    
    PermissionHandler getPermissionHandler();
    File getJarFile();
    File getDataDirectory();
    LoggingHandler getLoggingHandler();
    MessageHandler getMessageHandler();
    ConfigHandler getConfigHandler();
    LangConfigHandler getLangConfigHandler();
    void disable();
    Version getCurrentVersion();
    TaskHandler getTaskHandler();
    PlayerHandler getPlayerHandler();
    UpdateHandler getUpdateHandler();
    APIHandler getAPIHandler();
    /*
    Disable auto saving for worlds that have it enabled
     */
    void disableWorldAutoSave() throws InterruptedException, ExecutionException;
    /*
    Re-enable auto saving for worlds that had it enabled
     */
    void enableWorldAutoSave() throws InterruptedException, ExecutionException;
    ServerInformation getServerInfo();
}

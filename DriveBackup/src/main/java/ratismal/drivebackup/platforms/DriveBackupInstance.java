package ratismal.drivebackup.platforms;

import ratismal.drivebackup.configuration.ConfigHandler;
import ratismal.drivebackup.configuration.LangConfigHandler;
import ratismal.drivebackup.handler.logging.LoggingHandler;
import ratismal.drivebackup.handler.messages.MessageHandler;
import ratismal.drivebackup.handler.permission.PermissionHandler;
import ratismal.drivebackup.handler.task.TaskHandler;
import ratismal.drivebackup.util.Version;

import java.io.File;

public interface DriveBackupInstance {
    
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
}

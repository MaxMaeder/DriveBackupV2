package ratismal.drivebackup.platforms.bukkit;

import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.handler.logging.LoggingHandler;
import ratismal.drivebackup.handler.logging.PrefixedLogger;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public class BukkitLoggingHandler implements LoggingHandler {
    
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private final DriveBackupInstance driveBackupInstance;
    private final Logger logger;
    private final Map<String, PrefixedLogger> prefixedLoggers;
    
    public BukkitLoggingHandler(DriveBackupInstance driveBackupInstance, Logger logger) {
        this.logger = logger;
        this.driveBackupInstance = driveBackupInstance;
        prefixedLoggers = new HashMap<>(10);
    }
    
    @Override
    public void error(String message) {
        logger.severe(message);
    }
    
    @Override
    public void warn(String message) {
        logger.warning(message);
    }
    
    @Override
    public void info(String message) {
        logger.info(message);
    }
    
    @Override
    public void error(String message, Throwable throwable) {
        logger.severe(() -> handleError(message, throwable));
    }
    
    @Override
    public void warn(String message, Throwable throwable) {
        logger.warning(() -> handleError(message, throwable));
    }
    
    private String handleError(String message, Throwable throwable) {
        boolean suppress = driveBackupInstance.getConfigHandler().getConfig().node("advanced", "suppress-errors").getBoolean(false);
        if (suppress) {
            return message;
        }
        return message + LINE_SEPARATOR + throwable.getMessage() + LINE_SEPARATOR + throwable.getStackTrace();
    }
    
    @Override
    public void debug(String message) {
        if (driveBackupInstance.getConfigHandler().getConfig().node("advanced", "debug").getBoolean(false)) {
            logger.info(message);
        }
    }
    
    @Override
    public void debug(String message, Throwable throwable) {
        if (driveBackupInstance.getConfigHandler().getConfig().node("advanced", "debug").getBoolean(false)) {
            logger.info(() -> handleError(message, throwable));
        }
    }
    
    @Override
    public PrefixedLogger getPrefixedLogger(@NotNull String prefix) {
        // use lowercase to avoid case sensitivity in the map
        String prefixLowerCase = prefix.toLowerCase(Locale.ROOT);
        if (prefixedLoggers.containsKey(prefixLowerCase)) {
            return prefixedLoggers.get(prefixLowerCase);
        }
        PrefixedLogger prefixedLogger = new PrefixedLogger(prefix, this);
        prefixedLoggers.put(prefixLowerCase, prefixedLogger);
        return prefixedLogger;
    }
}

package ratismal.drivebackup.platforms.bukkit;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.configuration.ConfigurationSection;
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
    private ConfigurationSection advancedSection;
    
    @Contract (pure = true)
    public BukkitLoggingHandler(DriveBackupInstance driveBackupInstance, Logger logger) {
        this.logger = logger;
        this.driveBackupInstance = driveBackupInstance;
        prefixedLoggers = new HashMap<>(10);
        advancedSection = driveBackupInstance.getConfigHandler().getConfig().getSection("advanced");
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
        boolean suppress = advancedSection.getValue("suppress-errors").getBoolean();
        if (suppress) {
            return message;
        }
        StringBuilder sb = new StringBuilder(10_000);
        sb.append(message).append(LINE_SEPARATOR);
        sb.append("Exception: ").append(throwable).append(LINE_SEPARATOR);
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("    at ").append(element.toString()).append(LINE_SEPARATOR);
        }
        Throwable cause = throwable.getCause();
        int causeCount = 0;
        while (cause != null) {
            causeCount++;
            if (causeCount > 10) {
                sb.append("...").append(LINE_SEPARATOR);
                break;
            }
            sb.append("Caused by: ").append(cause).append(LINE_SEPARATOR);
            for (StackTraceElement element : cause.getStackTrace()) {
                sb.append(" at ").append(element.toString()).append(LINE_SEPARATOR);
            }
            cause = cause.getCause();
        }
        return sb.toString();
    }
    
    @Override
    public void debug(String message) {
        if (advancedSection.getValue("debug").getBoolean()) {
            logger.info(message);
        }
    }
    
    @Override
    public void debug(String message, Throwable throwable) {
        if (advancedSection.getValue("debug").getBoolean()) {
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

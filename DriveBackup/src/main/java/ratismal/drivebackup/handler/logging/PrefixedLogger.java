package ratismal.drivebackup.handler.logging;

import org.jetbrains.annotations.Contract;

public final class PrefixedLogger implements LoggingInterface {
    
    private final String prefix;
    private final LoggingHandler loggingHandler;
    
    @Contract (pure = true)
    public PrefixedLogger(String prefix, LoggingHandler loggingHandler) {
        this.prefix = "{" + prefix + "}";
        this.loggingHandler = loggingHandler;
    }
    
    @Override
    public void error(String message) {
        loggingHandler.error(prefix + " " + message);
    }
    
    @Override
    public void warn(String message) {
        loggingHandler.warn(prefix + " " + message);
    }
    
    @Override
    public void info(String message) {
        loggingHandler.info(prefix + " " + message);
    }
    
    @Override
    public void error(String message, Throwable throwable) {
        loggingHandler.error(prefix + " " + message, throwable);
    }
    
    @Override
    public void warn(String message, Throwable throwable) {
        loggingHandler.warn(prefix + " " + message, throwable);
    }
    
    @Override
    public void info(String message, Throwable throwable) {
        loggingHandler.info(prefix + " " + message, throwable);
    }
    
    @Override
    public void debug(String message) {
        loggingHandler.debug(prefix + " " + message);
    }
    
    @Override
    public void debug(String message, Throwable throwable) {
        loggingHandler.debug(prefix + " " + message, throwable);
    }
    
}

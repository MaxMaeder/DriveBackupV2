package ratismal.drivebackup.handler.logging;

public interface LoggingInterface {
    
    void error(String message);
    void warn(String message);
    void info(String message);
    void error(String message, Throwable throwable);
    void warn(String message, Throwable throwable);
    void debug(String message);
    void debug(String message, Throwable throwable);
}

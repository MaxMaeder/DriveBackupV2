package ratismal.drivebackup.handler.logging;

public interface LoggingHandler extends LoggingInterface {
    
    PrefixedLogger getPrefixedLogger(String prefix);
}

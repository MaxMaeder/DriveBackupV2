package ratismal.drivebackup.uploaders;

import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.constants.Initiator;
import ratismal.drivebackup.handler.messages.ConsoleLogLevel;
import ratismal.drivebackup.handler.messages.MessageHandler;
import ratismal.drivebackup.objects.Player;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.util.Map;

public final class UploadLogger {
    
    private final DriveBackupInstance instance;
    private Player player;
    private final Initiator initiator;
    private final MessageHandler messageHandler;
    
    public UploadLogger(@NotNull DriveBackupInstance instance, Initiator initiator) {
        this.instance = instance;
        this.initiator = initiator;
        messageHandler = instance.getMessageHandler();
    }
    
    public UploadLogger(@NotNull DriveBackupInstance instance, Player player) {
        this.instance = instance;
        this.player = player;
        initiator = Initiator.PLAYER;
        messageHandler = instance.getMessageHandler();
    }
    
    public void info(String key, String placeholder, String value) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key, placeholder, value);
        if (Initiator.PLAYER == initiator) {
            msg.to(player);
        }
        msg.toConsole(ConsoleLogLevel.INFO);
        msg.send();
    }
    
    public void info(String key, Map<String, String> placeholders) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key, placeholders);
        if (Initiator.PLAYER == initiator) {
            msg.to(player);
        }
        msg.toConsole(ConsoleLogLevel.INFO);
        msg.send();
    }
    
    public void info(String key) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key);
        if (Initiator.PLAYER == initiator) {
            msg.to(player);
        }
        msg.toConsole(ConsoleLogLevel.INFO);
        msg.send();
    }
    
    public void log(String key, String placeholder, String value) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key, placeholder, value);
        msg.toConsole(ConsoleLogLevel.INFO);
        msg.send();
    }
    
    public void log(String key, Map<String, String> placeholders) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key, placeholders);
        msg.toConsole(ConsoleLogLevel.INFO);
        msg.send();
    }
    
    public void log(String key) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key);
        msg.toConsole(ConsoleLogLevel.INFO);
        msg.send();
    }
    
    public void logRaw(String key) {
        MessageHandler msg = messageHandler.Builder();
        msg.miniMessage(key);
        msg.toConsole(ConsoleLogLevel.INFO);
        msg.send();
    }
    
    public void log(String key, Throwable throwable) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key);
        msg.addThrowable(throwable);
        msg.toConsole(ConsoleLogLevel.INFO);
        msg.send();
    }
    
    public void broadcast(String key, String placeholder, String value) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key, placeholder, value);
        msg.toAll();
        msg.toConsole(ConsoleLogLevel.INFO);
        msg.send();
    }
    
    public void broadcast(String key, Map<String, String> placeholders) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key, placeholders);
        msg.toAll();
        msg.toConsole(ConsoleLogLevel.INFO);
        msg.send();
    }
    
    public void broadcast(String key) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key);
        msg.toAll();
        msg.toConsole(ConsoleLogLevel.INFO);
        msg.send();
    }
    
    public void broadcastRaw(String message) {
        MessageHandler msg = messageHandler.Builder();
        msg.miniMessage(message);
        msg.toAll();
        msg.toConsole(ConsoleLogLevel.INFO);
        msg.send();
    }
    
    public void warn(String key, String placeholder, String value) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key, placeholder, value);
        if (Initiator.PLAYER == initiator) {
            msg.to(player);
        }
        msg.toConsole(ConsoleLogLevel.WARNING);
        msg.send();
    }
    
    public void warn(String key, Map<String, String> placeholders) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key, placeholders);
        if (Initiator.PLAYER == initiator) {
            msg.to(player);
        }
        msg.toConsole(ConsoleLogLevel.WARNING);
        msg.send();
    }
    
    public void warn(String key) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key);
        if (Initiator.PLAYER == initiator) {
            msg.to(player);
        }
        msg.toConsole(ConsoleLogLevel.WARNING);
        msg.send();
    }
    
    public void warn(String key, Throwable throwable) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key);
        msg.addThrowable(throwable);
        msg.toConsole(ConsoleLogLevel.WARNING);
        msg.send();
    }
    
    public void error(String key, String placeholder, String value) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key, placeholder, value);
        if (Initiator.PLAYER == initiator) {
            msg.to(player);
        }
        msg.toConsole(ConsoleLogLevel.ERROR);
        msg.send();
    }
    
    public void error(String key, Map<String, String> placeholders) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key, placeholders);
        if (Initiator.PLAYER == initiator) {
            msg.to(player);
        }
        msg.toConsole(ConsoleLogLevel.ERROR);
        msg.send();
    }
    
    public void error(String key) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key);
        if (Initiator.PLAYER == initiator) {
            msg.to(player);
        }
        msg.toConsole(ConsoleLogLevel.ERROR);
        msg.send();
    }
    
    public void error(String key, Throwable throwable) {
        MessageHandler msg = messageHandler.Builder();
        msg.getLang(key);
        msg.addThrowable(throwable);
        msg.toConsole(ConsoleLogLevel.ERROR);
        msg.send();
    }
    
}

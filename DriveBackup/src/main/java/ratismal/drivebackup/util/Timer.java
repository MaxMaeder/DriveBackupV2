package ratismal.drivebackup.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Timer {
    private Instant start;
    private Instant end;
    private final DriveBackupInstance instance;

    @Contract (pure = true)
    public Timer(DriveBackupInstance instance) {
        this.instance = instance;
    }

    /**
     * Starts the timer
     */
    public void start() {
        start = Instant.now();
        end = null;
    }

    /**
     * Ends the timer
     * @return false if timer was never started
     */
    public boolean end() {
        if (start == null) {
            return false;
        }
        end = Instant.now();
        return true;
    }

    /**
     * Construct an upload message
     * @param file file that was uploaded
     * @return message
     */
    public void sendUploadTimeMessage(@NotNull File file) {
        DecimalFormat df = new DecimalFormat("#.##");
        String configLanguage = instance.getConfigHandler().getConfig().getValue("advanced", "date-language").getString();
        Locale locale = Locale.forLanguageTag(configLanguage);
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(locale));
        long kb = file.length() / 1024L;
        long length = ChronoUnit.SECONDS.between(start, end);
        long speed;
        if (length >= 1) {
            speed = kb / length;
        } else {
            long milis = ChronoUnit.MILLIS.between(start, end);
            speed = (long) (kb / (milis / 1000.0));
        }
        Map<String, String> placeholders = new HashMap <>(3);
        placeholders.put("length", df.format(length));
        placeholders.put("speed", df.format(speed));
        placeholders.put("size", df.format(kb));
        instance.getMessageHandler().Builder().toConsole().getLang("file-upload-message", placeholders).send();
    }

    /**
     * Calculates the time
     * @return Calculated time in seconds
     */
    public long getTime() {
        return Duration.between(start, end).getSeconds();
    }
    
}

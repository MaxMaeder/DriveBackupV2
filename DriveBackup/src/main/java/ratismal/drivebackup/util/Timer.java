package ratismal.drivebackup.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

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
    public String getUploadTimeMessage(@NotNull File file) {
        DecimalFormat df = new DecimalFormat("#.##");
        Locale locale = Locale.forLanguageTag(instance.getConfigHandler().getConfig().getValue("advanced", "date-language").getString());
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(locale));
        long length = ChronoUnit.SECONDS.between(start, end);
        long speed = (file.length() / 1024L) / length;
        String message = instance.getMessageHandler().getLangString("file-upload-message");
        String message2 = message.replace("<length>", df.format(length));
        return message2.replace("<speed>", df.format(speed));
    }

}

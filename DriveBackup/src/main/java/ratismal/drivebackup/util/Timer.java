package ratismal.drivebackup.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import static ratismal.drivebackup.config.Localization.intl;

/**
 * Created by Ratismal on 2016-03-30.
 */

public class Timer {
    private Instant start;
    private Instant end;

    @Contract (pure = true)
    public Timer() {

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
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        long length = ChronoUnit.SECONDS.between(start, end);
        long speed = (file.length() / 1024) / length;
        return intl("file-upload-message")
            .replace("<length>", df.format(length))
            .replace("<speed>", df.format(speed));
    }

}

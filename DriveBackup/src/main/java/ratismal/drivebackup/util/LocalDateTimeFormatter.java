package ratismal.drivebackup.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.config.ConfigParser;

public final class LocalDateTimeFormatter {
    private static final String FORMAT_KEYWORD = "%FORMAT";
    private static final String FORMAT_REPLACEMENT = "'yyyy-M-d--HH-mm'";
    private static final Pattern VALID_FORMAT = Pattern.compile("^[\\w\\-.'% ]+$");

    private final DateTimeFormatter formatter;

    private LocalDateTimeFormatter(DateTimeFormatter formatter) {
        this.formatter = formatter;
    }

    @NotNull
    @Contract ("_ -> new")
    public static LocalDateTimeFormatter ofPattern(String pattern) throws IllegalArgumentException {
        if (!VALID_FORMAT.matcher(pattern).find()) {
            throw new IllegalArgumentException("Format pattern contains illegal characters");
        }
        if (pattern.contains(FORMAT_KEYWORD)) { // assumes %FORMAT not mixed with custom pattern
            int frontOffset = pattern.startsWith(FORMAT_KEYWORD) ? 2 : 0;
            int backOffset = pattern.endsWith(FORMAT_KEYWORD) ? 2 : 0;
            pattern = "'" + pattern.replace(FORMAT_KEYWORD, FORMAT_REPLACEMENT) + "'";
            pattern = pattern.substring(frontOffset, pattern.length() - backOffset);
        } else {
            pattern = "'" + pattern + "'";
        }
        return new LocalDateTimeFormatter(DateTimeFormatter.ofPattern(pattern));
    }

    public String format(@NotNull ZonedDateTime timeDate) {
        return timeDate.format(getFormatter());
    }

    public ZonedDateTime parse(String text) throws DateTimeParseException {
        return ZonedDateTime.parse(text, getFormatter());
    }

    @NotNull
    private DateTimeFormatter getFormatter() {
        return formatter.withLocale(ConfigParser.getConfig().advanced.dateLanguage).withZone(ConfigParser.getConfig().advanced.dateTimezone);
    }
}

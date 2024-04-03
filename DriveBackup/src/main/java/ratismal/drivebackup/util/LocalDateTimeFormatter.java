package ratismal.drivebackup.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.config.ConfigParser;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

public final class LocalDateTimeFormatter {
    private static final String FORMAT_KEYWORD = "%FORMAT";
    private static final String FORMAT_REPLACEMENT = "'yyyy-M-d--HH-mm'";
    private static final Pattern VALID_FORMAT = Pattern.compile("^[\\w\\-.'% ]+$");

    private final DateTimeFormatter formatter;

    @Contract (pure = true)
    private LocalDateTimeFormatter(DateTimeFormatter formatter) {
        this.formatter = formatter;
    }

    @NotNull
    @Contract ("_ -> new")
    public static LocalDateTimeFormatter ofPattern(String pattern) throws IllegalArgumentException {
        verifyPattern(pattern);
        if (pattern.contains(FORMAT_KEYWORD)) {
            int frontOffset = pattern.startsWith(FORMAT_KEYWORD) ? 2 : 0;
            int backOffset = pattern.endsWith(FORMAT_KEYWORD) ? 2 : 0;
            pattern = "'" + pattern.replace(FORMAT_KEYWORD, FORMAT_REPLACEMENT) + "'";
            pattern = pattern.substring(frontOffset, pattern.length() - backOffset);
        }
        return new LocalDateTimeFormatter(DateTimeFormatter.ofPattern(pattern));
    }

    public @NotNull String format(@NotNull ZonedDateTime timeDate) {
        return timeDate.format(getFormatter());
    }

    public @NotNull ZonedDateTime parse(String text) throws DateTimeParseException {
        return ZonedDateTime.parse(text, getFormatter());
    }

    @NotNull
    private DateTimeFormatter getFormatter() {
        return formatter.withLocale(ConfigParser.getConfig().advanced.dateLanguage).withZone(ConfigParser.getConfig().advanced.dateTimezone);
    }

    private static void verifyPattern(String pattern) throws IllegalArgumentException {
        boolean isValid = true;
        if (!VALID_FORMAT.matcher(pattern).find()) {
            isValid = false;
        }
        if (pattern.contains(FORMAT_KEYWORD)) {
            if (pattern.contains("'")) {
                isValid = false;
            }
        } else {
            if (pattern.contains("%") && !pattern.contains("%NAME")) {
                isValid = false;
            }
        }
        if (!isValid) {
            throw new IllegalArgumentException("Format pattern contains illegal characters");
        }
    }
}

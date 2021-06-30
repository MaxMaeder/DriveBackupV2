package ratismal.drivebackup.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import ratismal.drivebackup.config.ConfigParser;

public class LocalDateTimeFormatter {
    private final DateTimeFormatter formatter;

    private LocalDateTimeFormatter(DateTimeFormatter formatter) {
        this.formatter = formatter;
    }

    public static LocalDateTimeFormatter ofPattern(String pattern) throws IllegalArgumentException {
        StringBuilder finalPatternBuilder = new StringBuilder(pattern);

        // Escape non date format characters, if user specified %FORMAT in the pattern
        if (finalPatternBuilder.indexOf("%FORMAT") != -1) {
            finalPatternBuilder.insert(0, "'");
            finalPatternBuilder.insert(finalPatternBuilder.length(), "'");
        }

        String finalPattern = finalPatternBuilder.toString();
        finalPattern = finalPattern.replaceAll(Pattern.quote("%FORMAT"), "'yyyy-M-d--HH-mm'");

        return new LocalDateTimeFormatter(DateTimeFormatter.ofPattern(finalPattern));
    }

    public String format(ZonedDateTime timeDate) {
        return timeDate.format(getFormatter());
    }

    public ZonedDateTime parse(String text) throws DateTimeParseException {
        return ZonedDateTime.parse(text, getFormatter());
    }

    private DateTimeFormatter getFormatter() {
        return formatter.withLocale(ConfigParser.getConfig().advanced.dateLanguage);
    }
}
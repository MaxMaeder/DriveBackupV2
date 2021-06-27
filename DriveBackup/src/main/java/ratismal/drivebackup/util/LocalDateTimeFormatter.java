package ratismal.drivebackup.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import ratismal.drivebackup.config.ConfigParser;

public class LocalDateTimeFormatter {
    private final DateTimeFormatter formatter;

    private LocalDateTimeFormatter(DateTimeFormatter formatter) {
        this.formatter = formatter;
    }

    public static LocalDateTimeFormatter ofPattern(String pattern) throws IllegalArgumentException {
        StringBuilder finalPatternBuilder = new StringBuilder(pattern);

        // Escape non date format characters, if user specified {format} in the pattern
        if (finalPatternBuilder.indexOf("{format}") != -1) {
            finalPatternBuilder.insert(0, "'");
            finalPatternBuilder.insert(finalPatternBuilder.length(), "'");
        }

        String finalPattern = finalPatternBuilder.toString();
        finalPattern = finalPattern.replace("{format}", "'yyyy-M-d--HH-mm'");

        return new LocalDateTimeFormatter(DateTimeFormatter.ofPattern(finalPattern));
    }

    public String format(ZonedDateTime timeDate) {
        return timeDate.format(formatter.withLocale(ConfigParser.getConfig().advanced.dateLanguage));
    }
}
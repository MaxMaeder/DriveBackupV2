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
        return new LocalDateTimeFormatter(DateTimeFormatter.ofPattern(pattern));
    }

    public String format(ZonedDateTime timeDate) {
        return timeDate.format(formatter.withLocale(ConfigParser.getConfig().));
    }
}
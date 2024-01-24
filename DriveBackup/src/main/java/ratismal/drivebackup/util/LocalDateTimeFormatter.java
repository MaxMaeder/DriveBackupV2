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
    private static final Pattern VALID_FORMAT = Pattern.compile("^[\\w\\-.'% ]+$");

    private final DateTimeFormatter formatter;

    private LocalDateTimeFormatter(DateTimeFormatter formatter) {
        this.formatter = formatter;
    }

    @NotNull
    @Contract ("_ -> new")
    public static LocalDateTimeFormatter ofPattern(String pattern) throws IllegalArgumentException {
        verifyPattern(pattern);

        StringBuilder finalPatternBuilder = new StringBuilder(pattern);

        // Escape non date format characters, if user specified %FORMAT in the pattern
        if (pattern.contains(FORMAT_KEYWORD)) {
            finalPatternBuilder.insert(0, "'");
            finalPatternBuilder.append("'");
        }

        String finalPattern = finalPatternBuilder.toString();
        finalPattern = finalPattern.replaceAll(Pattern.quote("%FORMAT"), "'yyyy-M-d--HH-mm'");

        return new LocalDateTimeFormatter(DateTimeFormatter.ofPattern(finalPattern));
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

    private static void verifyPattern(String pattern) throws IllegalArgumentException {
        boolean isInvalid = false;

        if (!VALID_FORMAT.matcher(pattern).find()) {
            isInvalid = true;
        }

        if (pattern.contains(FORMAT_KEYWORD)) {
            if (pattern.contains("'")) {
                isInvalid = true;
            }
        } else {
            if (pattern.contains("%")) {
                isInvalid = true;
            }
        }

        if (isInvalid)
            throw new IllegalArgumentException("Format pattern contains illegal characters");
    }
}

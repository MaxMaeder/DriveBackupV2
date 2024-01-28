package ratismal.drivebackup.config.configSections;

import java.time.DateTimeException;
import java.time.ZoneOffset;
import java.util.Locale;

import org.bukkit.configuration.file.FileConfiguration;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.util.Logger;

import static ratismal.drivebackup.config.Localization.intl;

public class Advanced {
    public final boolean metricsEnabled;
    public final boolean updateCheckEnabled;
    public final boolean suppressErrors;
    public final boolean debugEnabled;
    public final Locale dateLanguage;
    public final ZoneOffset dateTimezone;
    public final String fileSeparator;

    public Advanced(
        boolean metricsEnabled, 
        boolean updateCheckEnabled, 
        boolean suppressErrors,
        boolean debugEnabled,
        Locale dateLanguage, 
        ZoneOffset dateTimezone, 
        String fileSeparator
        ) {
            
        this.metricsEnabled = metricsEnabled;
        this.updateCheckEnabled = updateCheckEnabled;
        this.suppressErrors = suppressErrors;
        this.debugEnabled = debugEnabled;
        this.dateLanguage = dateLanguage;
        this.dateTimezone = dateTimezone;
        this.fileSeparator = fileSeparator;
    }

    @NotNull
    @Contract ("_, _ -> new")
    public static Advanced parse(@NotNull FileConfiguration config, Logger logger) {
        boolean metrics = config.getBoolean("advanced.metrics");
        boolean updateCheck = config.getBoolean("advanced.update-check");
        boolean suppressErrors = config.getBoolean("advanced.suppress-errors");
        boolean debugEnabled = config.getBoolean("advanced.debug");
        Locale dateLanguage = new Locale(config.getString("advanced.date-language"));
        ZoneOffset dateTimezone;
        try {
            dateTimezone = ZoneOffset.of(config.getString("advanced.date-timezone"));
        } catch(DateTimeException e) {
            logger.log(intl("date-format-invalid"));
            //Fallback to UTC
            dateTimezone = ZoneOffset.of("Z");
        }
        String fileSeparator = config.getString("advanced.ftp-file-separator");
        return new Advanced(
            metrics, 
            updateCheck, 
            suppressErrors,
            debugEnabled,
            dateLanguage,
            dateTimezone, 
            fileSeparator
        );
    }
}

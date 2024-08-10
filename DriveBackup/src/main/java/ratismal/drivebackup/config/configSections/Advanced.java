package ratismal.drivebackup.config.configSections;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

@Deprecated
public class Advanced {
    public final boolean metricsEnabled;
    public final boolean updateCheckEnabled;
    public final boolean suppressErrors;
    public final boolean debugEnabled;
    public final Locale dateLanguage;
    public final ZoneId dateTimezone;
    public final String fileSeparator;

    @Contract (pure = true)
    public Advanced(
        boolean metricsEnabled,
        boolean updateCheckEnabled,
        boolean suppressErrors,
        boolean debugEnabled,
        Locale dateLanguage,
        ZoneId dateTimezone,
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
    @Contract ("_ -> new")
    public static Advanced parse(@NotNull FileConfiguration config) {
        boolean metrics = config.getBoolean("advanced.metrics");
        boolean updateCheck = config.getBoolean("advanced.update-check");
        boolean suppressErrors = config.getBoolean("advanced.suppress-errors");
        boolean debugEnabled = config.getBoolean("advanced.debug");
        Locale dateLanguage = new Locale(config.getString("advanced.date-language"));
        ZoneId dateTimezone;
        try {
            dateTimezone = ZoneId.of(config.getString("advanced.date-timezone"));
        } catch(DateTimeException e) {
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

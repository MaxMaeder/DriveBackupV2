package ratismal.drivebackup.config.configSections;

import java.time.ZoneOffset;
import java.util.Locale;

import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.ConfigParser.Logger;

public class Advanced {
    public final boolean metricsEnabled;
    public final boolean updateCheckEnabled;
    public final boolean suppressErrors;
    public final String messagePrefix;
    public final String defaultMessageColor;
    public final Locale dateLanguage;
    public final ZoneOffset dateTimezone;
    public final String fileSeparator;

    public Advanced(
        boolean metricsEnabled, 
        boolean updateCheckEnabled, 
        boolean suppressErrors, 
        String messagePrefix, 
        String defaultMessageColor, 
        Locale dateLanguage, 
        ZoneOffset dateTimezone, 
        String fileSeparator
        ) {
            
        this.metricsEnabled = metricsEnabled;
        this.updateCheckEnabled = updateCheckEnabled;
        this.suppressErrors = suppressErrors;
        this.messagePrefix = messagePrefix;
        this.defaultMessageColor = defaultMessageColor;
        this.dateLanguage = dateLanguage;
        this.dateTimezone = dateTimezone;
        this.fileSeparator = fileSeparator;
    }

    public static Advanced parse(FileConfiguration config, Logger logger) {
        boolean metrics = config.getBoolean("advanced.metrics");
        boolean updateCheck = config.getBoolean("advanced.update-check");
        boolean suppressErrors = config.getBoolean("advanced.suppress-errors");
        String messagePrefix = config.getString("advanced.message-prefix");
        String defaultMessageColor = config.getString("advanced.default-message-color");
        Locale dateLanguage = new Locale(config.getString("advanced.date-language"));
        
        ZoneOffset dateTimezone;
        try {
            dateTimezone = ZoneOffset.of(config.getString("advanced.date-format-timezone"));
        } catch(Exception e) {
            logger.log("Date format timezone not valid, using UTC");
            dateTimezone = ZoneOffset.of("Z"); //Fallback to UTC
        }

        String fileSeparator = config.getString("advanced.ftp-file-separator");

        return new Advanced(
            metrics, 
            updateCheck, 
            suppressErrors, 
            messagePrefix, 
            defaultMessageColor, 
            dateLanguage,
            dateTimezone, 
            fileSeparator
            );
    }
}
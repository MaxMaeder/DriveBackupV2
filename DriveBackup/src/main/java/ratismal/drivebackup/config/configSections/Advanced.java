package ratismal.drivebackup.config.configSections;

import java.util.Locale;

import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.ConfigParser.Logger;

public class Advanced {
    public final boolean metrics;
    public final boolean updateCheck;
    public final boolean suppressErrors;
    public final String messagePrefix;
    public final String defaultMessageColor;
    public final Locale dateLanguage;
    public final String fileSeparator;

    private Advanced(boolean metrics,boolean updateCheck, boolean suppressErrors, String messagePrefix, String defaultMessageColor, Locale dateLanguage, String fileSeparator) {
        this.metrics = metrics;
        this.updateCheck = updateCheck;
        this.suppressErrors = suppressErrors;
        this.messagePrefix = messagePrefix;
        this.defaultMessageColor = defaultMessageColor;
        this.dateLanguage = dateLanguage;
        this.fileSeparator = fileSeparator;
    }

    public static Advanced parse(FileConfiguration config, Logger logger) {
        boolean metrics = config.getBoolean("advanced.metrics");
        boolean updateCheck = config.getBoolean("advanced.update-check");
        boolean suppressErrors = config.getBoolean("advanced.suppress-errors");
        String messagePrefix = config.getString("advanced.message-prefix");
        String defaultMessageColor = config.getString("advanced.default-message-color");
        Locale dateLanguage = new Locale(config.getString("date-language"));
        String fileSeparator = config.getString("advanced.ftp-file-separator");

        return new Advanced(
            metrics, 
            updateCheck, 
            suppressErrors, 
            messagePrefix, 
            defaultMessageColor, 
            dateLanguage, 
            fileSeparator
            );
    }
}
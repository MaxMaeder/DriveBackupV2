package ratismal.drivebackup.config;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.zip.Deflater;
import java.util.zip.ZipOutputStream;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.Config.BackupScheduling;
import ratismal.drivebackup.config.Config.BackupStorage;
import ratismal.drivebackup.config.Config.BackupList.BackupListEntry;
import ratismal.drivebackup.config.Config.BackupScheduling.BackupScheduleEntry;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.SchedulerUtil;

public class ConfigParser {
    private FileConfiguration config;
    private Configuration defaultConfig;
    private Config parsedConfig;

    /**
     * Creates an instance of the {@code Config} object
     * @param config A reference to the plugin's {@code config.yml}
     */
    public ConfigParser(FileConfiguration config) {
        this.config = config;
        this.defaultConfig = config.getDefaults();
    }

    /**
     * Reloads the plugin's {@code config.yml}
     * @param config A reference to the plugin's {@code config.yml}
     */
    public void reload(FileConfiguration config, CommandSender initiator) {
        this.config = config;
        this.defaultConfig = config.getDefaults();
        reload(initiator);
    }

    /**
     * Gets the plugion's parsed config
     * @return the config
     */
    public Config getConfig() {
        return parsedConfig;
    }

    /**
     * Reloads the plugin's {@code config.yml}
     */
    public void reload(CommandSender initiator) {

    } 

    private BackupStorage parseBackupStorage(CommandSender initiator) {
        long delay = config.getLong("delay");
        if (delay <= 5) {
            MessageUtil.sendMessage(initiator, "Inputted backup delay invalid, using default");
            delay = defaultConfig.getLong("delay");
        }

        int threadPriority = config.getInt("backup-thread-priority");
        if (threadPriority < Thread.MIN_PRIORITY) {
            MessageUtil.sendMessage(initiator, "Inputted thread priority less than minimum, using minimum");
            threadPriority = Thread.MIN_PRIORITY;
        } else if (threadPriority > Thread.MAX_PRIORITY) {
            MessageUtil.sendMessage(initiator, "Inputted thread priority more than maximum, using maximum");
            threadPriority = Thread.MAX_PRIORITY;
        }

        int keepCount = config.getInt("keep-count");
        if (keepCount < -1) {
            MessageUtil.sendMessage(initiator, "Keep count invalid, using default");
            keepCount = defaultConfig.getInt("keep-count");
        }

        int localKeepCount = config.getInt("local-keep-count");
        if (localKeepCount < -1) {
            MessageUtil.sendMessage(initiator, "Inputted local keep count invalid, using default");
            localKeepCount = defaultConfig.getInt("local-keep-count");
        }

        int zipCompression = config.getInt("zip-compression");
        if (zipCompression < Deflater.BEST_SPEED) {
            MessageUtil.sendMessage(initiator, "Inputted zip compression less than minimum, using minimum");
            zipCompression = Deflater.BEST_SPEED;
        } else if (zipCompression > Deflater.BEST_COMPRESSION) {
            MessageUtil.sendMessage(initiator, "Inputted zip compression more than maximum, using maximum");
            zipCompression = Deflater.BEST_COMPRESSION;
        }

        boolean backupsRequirePlayers = config.getBoolean("backups-require-players");
        boolean disableSavingDuringBackups = config.getBoolean("disable-saving-during-backups");

        String localDirectory = config.getString("dir");
        String remoteDirectory = config.getString("directory");

        return new BackupStorage(delay, threadPriority, keepCount, localKeepCount, zipCompression, backupsRequirePlayers, disableSavingDuringBackups, localDirectory, remoteDirectory);
    }

    private BackupScheduling parseBackupScheduling(CommandSender initiator) {
        boolean schedulingEnabled = config.getBoolean("scheduled-backups");

        ZoneOffset scheduleTimezone;
        try {
            scheduleTimezone = ZoneOffset.of(config.getString("schedule-timezone"));
        } catch(Exception e) {
            MessageUtil.sendMessage(initiator, "Inputted schedule timezone not valid, using UTC");
            scheduleTimezone = ZoneOffset.of("Z"); //Fallback to UTC
        }

        List<Map<?, ?>> rawSchedule = config.getMapList("backup-schedule-list");
        ArrayList<BackupScheduleEntry> schedule = new ArrayList<>();
        for (Map<?, ?> rawScheduleEntry : rawSchedule) {
            
            List<String> rawDays;
            try {
                rawDays = (List<String>) rawScheduleEntry.get("days");
            } catch (Exception e) {
                MessageUtil.sendMessage(initiator, "Days list invalid, skipping schedule entry");
                continue;
            }

            Set<DayOfWeek> days = new HashSet<DayOfWeek>();
            for (String rawDay : rawDays) {
                try {
                    days.add(DayOfWeek.valueOf(rawDay));
                } catch (Exception e) {
                    MessageUtil.sendMessage(initiator, "Day of week invalid, skipping day of week");
                }
            }

            if (days.size() == 0) {
                MessageUtil.sendMessage(initiator, "Day of week list empty, skipping schedule entry");
                continue;
            }

            TemporalAccessor time;
            try {
                time = SchedulerUtil.parseTime((String) rawScheduleEntry.get("time"));
            } catch (Exception e) {
                MessageUtil.sendMessage(initiator, "Time invalid, skipping schedule entry");
                continue;
            }

            schedule.add(new BackupScheduling.BackupScheduleEntry(
                (DayOfWeek[]) days.toArray(),
                time
                ));
        }

        if (rawSchedule.size() == 0) {
            MessageUtil.sendMessage(initiator, "Backup schedule empty, disabling schedule-based backups");
            schedulingEnabled = false;
        }

        return new BackupScheduling(
            schedulingEnabled, 
            scheduleTimezone, 
            (BackupScheduleEntry[]) schedule.toArray()
            );
    }
}

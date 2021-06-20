package ratismal.drivebackup.config.configSections;

import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.ConfigParser.Logger;
import ratismal.drivebackup.util.SchedulerUtil;

public class BackupScheduling {
    public static class BackupScheduleEntry {
        public final DayOfWeek[] days;
        public final TemporalAccessor time;

        private BackupScheduleEntry(DayOfWeek[] days, TemporalAccessor time) {
            this.days = days;
            this.time = time;
        }
    }

    public final boolean enabled;
    public final ZoneOffset timezone;
    public final BackupScheduleEntry[] schedule;

    private BackupScheduling(
        boolean enabled, 
        ZoneOffset timezone,
        BackupScheduleEntry[] schedule
        ) {

        this.enabled = enabled;
        this.timezone = timezone;
        this.schedule = schedule;
    }

    public static BackupScheduling parse(FileConfiguration config, Logger logger) {
        boolean schedulingEnabled = config.getBoolean("scheduled-backups");

        ZoneOffset scheduleTimezone;
        try {
            scheduleTimezone = ZoneOffset.of(config.getString("schedule-timezone"));
        } catch(Exception e) {
            logger.log("Inputted schedule timezone not valid, using UTC");
            scheduleTimezone = ZoneOffset.of("Z"); //Fallback to UTC
        }

        List<Map<?, ?>> rawSchedule = config.getMapList("backup-schedule-list");
        ArrayList<BackupScheduleEntry> schedule = new ArrayList<>();
        for (Map<?, ?> rawScheduleEntry : rawSchedule) {
            int entryIndex = rawSchedule.indexOf(rawScheduleEntry) + 1;
            
            List<String> rawDays;
            try {
                rawDays = (List<String>) rawScheduleEntry.get("days");
            } catch (Exception e) {
                logger.log("Days list invalid, skipping schedule entry index " + entryIndex);
                continue;
            }

            Set<DayOfWeek> days = new HashSet<DayOfWeek>();
            for (String rawDay : rawDays) {
                try {
                    days.add(DayOfWeek.valueOf(rawDay));
                } catch (Exception e) {
                    logger.log("Day of week invalid, skipping day of week \"" + rawDay + "\"");
                }
            }

            if (days.size() == 0) {
                logger.log("Day of week list empty, skipping schedule entry index " + entryIndex);
                continue;
            }

            TemporalAccessor time;
            try {
                time = SchedulerUtil.parseTime((String) rawScheduleEntry.get("time"));
            } catch (Exception e) {
                logger.log("Time invalid, skipping schedule entry");
                continue;
            }

            schedule.add(new BackupScheduling.BackupScheduleEntry(
                (DayOfWeek[]) days.toArray(),
                time
                ));
        }

        if (rawSchedule.size() == 0) {
            logger.log("Backup schedule empty, disabling schedule-based backups");
            schedulingEnabled = false;
        }

        return new BackupScheduling(
            schedulingEnabled, 
            scheduleTimezone, 
            (BackupScheduleEntry[]) schedule.toArray()
            );
    }
}
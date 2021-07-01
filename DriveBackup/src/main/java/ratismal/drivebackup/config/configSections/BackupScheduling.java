package ratismal.drivebackup.config.configSections;

import java.time.DayOfWeek;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.ConfigParser.Logger;
import ratismal.drivebackup.util.SchedulerUtil;

public class BackupScheduling {
    public static class BackupScheduleEntry {
        public final DayOfWeek[] days;
        public final TemporalAccessor time;

        public BackupScheduleEntry(DayOfWeek[] days, TemporalAccessor time) {
            this.days = days;
            this.time = time;
        }
    }

    public final boolean enabled;
    public final BackupScheduleEntry[] schedule;

    public BackupScheduling(
        boolean enabled, 
        BackupScheduleEntry[] schedule
        ) {

        this.enabled = enabled;
        this.schedule = schedule;
    }

    public static BackupScheduling parse(FileConfiguration config, Logger logger) {
        boolean enabled = config.getBoolean("scheduled-backups");

        List<Map<?, ?>> rawSchedule = config.getMapList("backup-schedule-list");
        List<BackupScheduleEntry> schedule = new ArrayList<>();
        for (Map<?, ?> rawScheduleEntry : rawSchedule) {
            int entryIndex = rawSchedule.indexOf(rawScheduleEntry) + 1;
            
            List<String> rawDays;
            try {
                rawDays = (List<String>) rawScheduleEntry.get("days");
            } catch (Exception e) {
                logger.log("Days list invalid, skipping schedule entry index " + entryIndex);
                continue;
            }

            Set<DayOfWeek> days = new HashSet<>();
            for (String rawDay : rawDays) {
                try {
                    boolean isDayGroup = false;

                    if (rawDay.equals("weekdays") || rawDay.equals("everyday")) {
                        isDayGroup = true;

                        days.add(DayOfWeek.MONDAY);
                        days.add(DayOfWeek.TUESDAY);
                        days.add(DayOfWeek.WEDNESDAY);
                        days.add(DayOfWeek.THURSDAY);
                        days.add(DayOfWeek.FRIDAY);
                    }

                    if (rawDay.equals("weekends") || rawDay.equals("everyday")) {
                        isDayGroup = true;

                        days.add(DayOfWeek.SATURDAY);
                        days.add(DayOfWeek.SUNDAY);
                    }

                    if (!isDayGroup) {
                        days.add(DayOfWeek.valueOf(rawDay.toUpperCase(Locale.ROOT)));
                    }
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

        if (rawSchedule.size() == 0 && enabled) {
            logger.log("Backup schedule empty, disabling schedule-based backups");
            enabled = false;
        }



        return new BackupScheduling(
            enabled, 
            schedule.toArray(new BackupScheduleEntry[0])
            );
    }
}
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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.util.Logger;
import ratismal.drivebackup.util.SchedulerUtil;

import static ratismal.drivebackup.config.Localization.intl;

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

    @NotNull
    @Contract ("_, _ -> new")
    public static BackupScheduling parse(@NotNull FileConfiguration config, Logger logger) {
        boolean enabled = config.getBoolean("scheduled-backups");
        List<Map<?, ?>> rawSchedule = config.getMapList("backup-schedule-list");
        List<BackupScheduleEntry> schedule = new ArrayList<>();
        for (Map<?, ?> rawScheduleEntry : rawSchedule) {
            String entryIndex = String.valueOf(rawSchedule.indexOf(rawScheduleEntry) + 1);
            List<String> rawDays;
            try {
                rawDays = (List<String>) rawScheduleEntry.get("days");
            } catch (ClassCastException e) {
                logger.log(intl("backup-schedule-days-invalid"), "entry", entryIndex);
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
                } catch (IllegalArgumentException e) {
                    logger.log(intl("backup-schedule-day-invalid"));
                }
            }
            if (days.isEmpty()) {
                logger.log(intl("backup-schedule-day-empty"), "entry", entryIndex);
                continue;
            }
            TemporalAccessor time;
            try {
                time = SchedulerUtil.parseTime((String) rawScheduleEntry.get("time"));
            } catch (IllegalArgumentException | ClassCastException e) {
                logger.log(intl("backup-schedule-time-invalid"), "entry", entryIndex);
                continue;
            }
            schedule.add(new BackupScheduling.BackupScheduleEntry(
                days.toArray(new DayOfWeek[0]),
                time
                ));
        }
        if (rawSchedule.isEmpty() && enabled) {
            logger.log(intl("backup-schedule-empty"));
            enabled = false;
        }
        return new BackupScheduling(
            enabled, 
            schedule.toArray(new BackupScheduleEntry[0])
            );
    }
}

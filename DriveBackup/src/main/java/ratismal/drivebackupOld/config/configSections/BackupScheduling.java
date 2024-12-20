package ratismal.drivebackupOld.config.configSections;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.objects.BackupScheduleEntry;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Deprecated
public class BackupScheduling {
    
    public final boolean enabled;
    public final BackupScheduleEntry[] schedule;

    @Contract (pure = true)
    public BackupScheduling(
        boolean enabled, 
        BackupScheduleEntry[] schedule
        ) {

        this.enabled = enabled;
        this.schedule = schedule;
    }

    @NotNull
    @Contract ("_ -> new")
    public static BackupScheduling parse(@NotNull FileConfiguration config) {
        boolean enabled = config.getBoolean("scheduled-backups");
        List<Map<?, ?>> rawSchedule = config.getMapList("backup-schedule-list");
        List<BackupScheduleEntry> schedule = new ArrayList<>();
        for (Map<?, ?> rawScheduleEntry : rawSchedule) {
            String entryIndex = String.valueOf(rawSchedule.indexOf(rawScheduleEntry) + 1);
            List<String> rawDays;
            try {
                rawDays = (List<String>) rawScheduleEntry.get("days");
            } catch (ClassCastException e) {
                continue;
            }
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
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
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (days.isEmpty()) {
                continue;
            }
            TemporalAccessor time;
            try {
                time = parseTime((String) rawScheduleEntry.get("time"));
            } catch (IllegalArgumentException | ClassCastException e) {
                continue;
            }
            schedule.add(new BackupScheduleEntry(
                days.toArray(new DayOfWeek[0]),
                null
                ));
        }
        if (rawSchedule.isEmpty() && enabled) {
            enabled = false;
        }
        return new BackupScheduling(
            enabled, 
            schedule.toArray(new BackupScheduleEntry[0])
            );
    }
    
    public static TemporalAccessor parseTime(String time) throws IllegalArgumentException {
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                .appendOptional(DateTimeFormatter.ofPattern("kk:mm"))
                .appendOptional(DateTimeFormatter.ofPattern("k:mm"))
                .toFormatter();
        return formatter.parse(time);
    }
}

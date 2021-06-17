package ratismal.drivebackup.plugin;

import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.SchedulerUtil;

public class Scheduler {
    /**
     * List of the IDs of the scheduled backup tasks
     */
    private static ArrayList<Integer> backupTasks = new ArrayList<>();

    /**
     * List of Dates representing each time a scheduled backup will occur
     */
    private static ArrayList<ZonedDateTime> backupDatesList = new ArrayList<>();

    /**
     * Starts the backup thread
     */
    public static void startBackupThread() {
        BukkitScheduler taskScheduler = Bukkit.getServer().getScheduler();

        if (Config.isBackupsScheduled()) {
            SchedulerUtil.cancelTasks(backupTasks);
            backupDatesList.clear();

            ZoneOffset timezone = Config.getBackupScheduleTimezone();

            for (HashMap<String, Object> schedule : Config.getBackupScheduleList()) {

                LinkedHashSet<String> scheduleDays = new LinkedHashSet<>();
                scheduleDays.addAll((List<String>) schedule.get("days"));

                for (String entry : new LinkedHashSet<>(scheduleDays)) {
                    switch (entry) {
                        case "weekdays":
                            scheduleDays.remove(entry);
                            scheduleDays.addAll(Arrays.asList("monday", "tuesday", "wednesday", "thursday", "friday"));
                            break;
                        case "weekends":
                            scheduleDays.remove(entry);
                            scheduleDays.addAll(Arrays.asList("sunday", "saturday"));
                            break;
                        case "everyday":
                            scheduleDays.remove(entry);
                            scheduleDays.addAll(Arrays.asList("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"));
                            break;
                    }
                }

                TemporalAccessor scheduleTime = DateTimeFormatter.ofPattern ("kk:mm", Locale.ENGLISH).parse((String) schedule.get("time"));

                for (String entry : new LinkedHashSet<>(scheduleDays)) {
                    ZonedDateTime previousOccurrence = ZonedDateTime.now(timezone)
                        .with(TemporalAdjusters.previous(DayOfWeek.valueOf(entry.toUpperCase())))
                        .with(ChronoField.CLOCK_HOUR_OF_DAY, scheduleTime.get(ChronoField.CLOCK_HOUR_OF_DAY))
                        .with(ChronoField.MINUTE_OF_HOUR, scheduleTime.get(ChronoField.MINUTE_OF_HOUR))
                        .with(ChronoField.SECOND_OF_MINUTE, 0);

                    ZonedDateTime now = ZonedDateTime.now(timezone);
                    
                    ZonedDateTime nextOccurrence = ZonedDateTime.now(timezone)
                        .with(TemporalAdjusters.nextOrSame(DayOfWeek.valueOf(entry.toUpperCase())))
                        .with(ChronoField.CLOCK_HOUR_OF_DAY, scheduleTime.get(ChronoField.CLOCK_HOUR_OF_DAY))
                        .with(ChronoField.MINUTE_OF_HOUR, scheduleTime.get(ChronoField.MINUTE_OF_HOUR))
                        .with(ChronoField.SECOND_OF_MINUTE, 0);

                    // Adjusts nextOccurrence date when it was set to earlier on same day, as the DayOfWeek TemporalAdjuster only takes into account the day, not the time
                    ZonedDateTime startingOccurrence = nextOccurrence;
                    if (now.isAfter(startingOccurrence)) {
                        startingOccurrence = startingOccurrence.plusWeeks(1);
                    }

                    backupTasks.add(taskScheduler.runTaskTimerAsynchronously(
                        DriveBackup.getInstance(), 
                        new UploadThread(),
                        ChronoUnit.SECONDS.between(now, startingOccurrence) * 20, // 20 ticks per second 
                        ChronoUnit.SECONDS.between(previousOccurrence, nextOccurrence) * 20
                    ).getTaskId());

                    backupDatesList.add(startingOccurrence);
                }

                ZonedDateTime scheduleMessageTime = ZonedDateTime.now(timezone)
                    .with(ChronoField.CLOCK_HOUR_OF_DAY, scheduleTime.get(ChronoField.CLOCK_HOUR_OF_DAY))
                    .with(ChronoField.MINUTE_OF_HOUR, scheduleTime.get(ChronoField.MINUTE_OF_HOUR));
                StringBuilder scheduleMessage = new StringBuilder();
                scheduleMessage.append("Scheduling a backup to run at ");
                scheduleMessage.append(scheduleMessageTime.format(DateTimeFormatter.ofPattern("hh:mm a")));
                scheduleMessage.append(" every ");
                ArrayList<String> daysFormatted = new ArrayList<>();
                for (String word : scheduleDays) {
                    daysFormatted.add(word.substring(0, 1).toUpperCase() + word.substring(1));
                }
                scheduleMessage.append(String.join(", ", daysFormatted));
                MessageUtil.sendConsoleMessage(scheduleMessage.toString());
            }
        } else if (Config.getBackupDelay() != -1) {
            SchedulerUtil.cancelTasks(backupTasks);

            MessageUtil.sendConsoleMessage("Scheduling a backup to run every " + Config.getBackupDelay() + " minutes");

            long interval = SchedulerUtil.sToTicks(Config.getBackupDelay() * 60);

            backupTasks.add(taskScheduler.runTaskTimerAsynchronously(
                DriveBackup.getInstance(), 
                new UploadThread(),
                interval,
                interval
            ).getTaskId());

            UploadThread.updateNextIntervalBackupTime();
        }
    }

    /**
     * Stops the backup thread
     */
    public static void stopBackupThread() {
        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.cancelTasks(DriveBackup.getInstance());
    }

    /**
     * Gets a list of Dates representing each time a scheduled backup will occur
     * @return the ArrayList of {@code ZonedDateTime} objects
     */
    public static ArrayList<ZonedDateTime> getBackupDatesList() {
        return backupDatesList;
    }
}
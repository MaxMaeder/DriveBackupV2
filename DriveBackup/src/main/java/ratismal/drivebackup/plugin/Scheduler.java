package ratismal.drivebackup.plugin;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import org.bukkit.Bukkit;

import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.config.configSections.BackupScheduling.BackupScheduleEntry;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.SchedulerUtil;

import static ratismal.drivebackup.config.Localization.intl;

public class Scheduler {
    /**
     * How often to run the schedule drift correction task, in seconds.
     */
    private static final long SCHEDULE_DRIFT_CORRECTION_INTERVAL = 1 * 60 * 60 * 24;

    /**
     * List of the IDs of the scheduled backup tasks
     */
    private static List<WrappedTask> backupTasks = new ArrayList<>();

    /**
     * ID of the schedule drift correction task
     */
    private static WrappedTask scheduleDriftTask = null;

    /**
     * List of Dates representing each time a scheduled backup will occur.
     */
    private static ArrayList<ZonedDateTime> backupDatesList = new ArrayList<>();

    /**
     * Starts the backup thread
     */
    public static void startBackupThread() {
        Config config = ConfigParser.getConfig();
        if (config.backupScheduling.enabled) {
            for (WrappedTask task : backupTasks) {
                task.cancel();
            }
            backupTasks.clear();
            backupDatesList.clear();
            for (BackupScheduleEntry entry : config.backupScheduling.schedule) {
                ZoneId timezone = config.advanced.dateTimezone;
                for (DayOfWeek day : entry.days) {
                    ZonedDateTime previousOccurrence = ZonedDateTime.now(timezone)
                        .with(TemporalAdjusters.previous(day))
                        .with(ChronoField.CLOCK_HOUR_OF_DAY, entry.time.get(ChronoField.CLOCK_HOUR_OF_DAY))
                        .with(ChronoField.MINUTE_OF_HOUR, entry.time.get(ChronoField.MINUTE_OF_HOUR))
                        .with(ChronoField.SECOND_OF_MINUTE, 0);
                    ZonedDateTime now = ZonedDateTime.now(timezone);
                    ZonedDateTime nextOccurrence = ZonedDateTime.now(timezone)
                        .with(TemporalAdjusters.nextOrSame(day))
                        .with(ChronoField.CLOCK_HOUR_OF_DAY, entry.time.get(ChronoField.CLOCK_HOUR_OF_DAY))
                        .with(ChronoField.MINUTE_OF_HOUR, entry.time.get(ChronoField.MINUTE_OF_HOUR))
                        .with(ChronoField.SECOND_OF_MINUTE, 0);
                    // Adjusts nextOccurrence date when it was set to earlier on the same day,
                    // as the DayOfWeek TemporalAdjuster only takes into account the day,
                    // not the time.
                    ZonedDateTime startingOccurrence = nextOccurrence;
                    if (now.isAfter(startingOccurrence)) {
                        startingOccurrence = startingOccurrence.plusWeeks(1);
                    }
                    backupTasks.add(DriveBackup.getFoliaLib().getScheduler().runTimerAsync(
                        new UploadThread(),
                        SchedulerUtil.sToTicks(ChronoUnit.SECONDS.between(now, startingOccurrence)),
                        SchedulerUtil.sToTicks(ChronoUnit.SECONDS.between(previousOccurrence, nextOccurrence))
                    ));
                    backupDatesList.add(startingOccurrence);
                }
                ZonedDateTime scheduleMessageTime = ZonedDateTime.now(timezone)
                    .with(ChronoField.CLOCK_HOUR_OF_DAY, entry.time.get(ChronoField.CLOCK_HOUR_OF_DAY))
                    .with(ChronoField.MINUTE_OF_HOUR, entry.time.get(ChronoField.MINUTE_OF_HOUR));
                StringBuilder scheduleDays = new StringBuilder();
                for (int i = 0; i < entry.days.length; i++) {
                    if (i == entry.days.length - 1) {
                        scheduleDays.append(intl("list-last-delimiter"));
                    } else if (i != 0) {
                        scheduleDays.append(intl("list-delimiter"));
                    }
                    String dayName = entry.days[i].getDisplayName(TextStyle.FULL, config.advanced.dateLanguage);
                    scheduleDays.append(dayName);
                }
                MessageUtil.Builder()
                    .mmText(
                        intl("backups-scheduled"), 
                        "time", scheduleMessageTime.format(DateTimeFormatter.ofPattern("hh:mm a")), 
                        "days", scheduleDays.toString())
                    .toConsole(true)
                    .send();
            }
            if (scheduleDriftTask != null) {
                scheduleDriftTask.cancel();
            }
            long driftInt = SchedulerUtil.sToTicks(SCHEDULE_DRIFT_CORRECTION_INTERVAL);
            scheduleDriftTask = DriveBackup.getFoliaLib().getScheduler().runTimer(() -> startBackupThread(), driftInt, driftInt);
        } else if (config.backupStorage.delay != -1) {
            for (WrappedTask task : backupTasks) {
                task.cancel();
            }
            backupTasks.clear();
            if (scheduleDriftTask != null) {
                scheduleDriftTask.cancel();
                scheduleDriftTask = null;
            }
            MessageUtil.Builder()
                .mmText(intl("backups-interval-scheduled"), "delay", String.valueOf(config.backupStorage.delay))
                .toConsole(true)
                .send();
            long interval = SchedulerUtil.sToTicks(config.backupStorage.delay * 60);
            backupTasks.add(DriveBackup.getFoliaLib().getScheduler().runTimerAsync(
                    new UploadThread(),
                    interval,
                    interval
            ));
            UploadThread.updateNextIntervalBackupTime();
        }
    }

    /**
     * Stops the backup thread
     */
    public static void stopBackupThread() {
        for (WrappedTask task : backupTasks) {
            task.cancel();
        }
        backupTasks.clear();
        if (scheduleDriftTask != null) {
            scheduleDriftTask.cancel();
            scheduleDriftTask = null;
        }
    }

    /**
     * Gets a list of Dates representing each time a scheduled backup will occur.
     * @return the ArrayList of {@code ZonedDateTime} objects
     */
    public static List<ZonedDateTime> getBackupDatesList() {
        return backupDatesList;
    }
}

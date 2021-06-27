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
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.config.configSections.BackupScheduling.BackupScheduleEntry;
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
        Config config = ConfigParser.getConfig();
        BukkitScheduler taskScheduler = Bukkit.getServer().getScheduler();

        if (config.backupScheduling.enabled) {
            SchedulerUtil.cancelTasks(backupTasks);
            backupDatesList.clear();

            for (BackupScheduleEntry entry : config.backupScheduling.schedule) {

                ZoneOffset timezone = config.advanced.dateTimezone;

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

                    // Adjusts nextOccurrence date when it was set to earlier on same day, as the DayOfWeek TemporalAdjuster only takes into account the day, not the time
                    ZonedDateTime startingOccurrence = nextOccurrence;
                    if (now.isAfter(startingOccurrence)) {
                        startingOccurrence = startingOccurrence.plusWeeks(1);
                    }

                    backupTasks.add(taskScheduler.runTaskTimerAsynchronously(
                        DriveBackup.getInstance(), 
                        new UploadThread(),
                        SchedulerUtil.sToTicks(ChronoUnit.SECONDS.between(now, startingOccurrence)),
                        SchedulerUtil.sToTicks(ChronoUnit.SECONDS.between(previousOccurrence, nextOccurrence))
                    ).getTaskId());

                    backupDatesList.add(startingOccurrence);
                }

                ZonedDateTime scheduleMessageTime = ZonedDateTime.now(timezone)
                    .with(ChronoField.CLOCK_HOUR_OF_DAY, entry.time.get(ChronoField.CLOCK_HOUR_OF_DAY))
                    .with(ChronoField.MINUTE_OF_HOUR, entry.time.get(ChronoField.MINUTE_OF_HOUR));

                StringBuilder scheduleMessage = new StringBuilder();
                scheduleMessage.append("Scheduling a backup to run at ");
                scheduleMessage.append(scheduleMessageTime.format(DateTimeFormatter.ofPattern("hh:mm a")));
                scheduleMessage.append(" every ");

                for (int i = 0; i < scheduleDays.size(); i++) {
                    if (i != 0) {
                        scheduleMessage.append(", ");
                    }
                    scheduleMessage.append(scheduleDays.get(i).substring(0, 1).toUpperCase() + scheduleDays.get(i).substring(1));
                }
                MessageUtil.sendConsoleMessage(scheduleMessage.toString());
            }
        } else if (config.backupStorage.delay != -1) {
            SchedulerUtil.cancelTasks(backupTasks);

            MessageUtil.sendConsoleMessage("Scheduling a backup to run every " + config.backupStorage.delay + " minutes");

            long interval = SchedulerUtil.sToTicks(config.backupStorage.delay * 60);

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

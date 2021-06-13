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
import java.util.HashMap;
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

                ArrayList<String> scheduleDays = new ArrayList<>();
                scheduleDays.addAll((List<String>) schedule.get("days"));

                for (int i = 0; i < scheduleDays.size(); i++) {
                    switch (scheduleDays.get(i)) {
                        case "weekdays":
                            scheduleDays.remove(scheduleDays.get(i));
                            addIfNotAdded(scheduleDays, "monday");
                            addIfNotAdded(scheduleDays, "tuesday");
                            addIfNotAdded(scheduleDays, "wednesday");
                            addIfNotAdded(scheduleDays, "thursday");
                            addIfNotAdded(scheduleDays, "friday");
                            break;
                        case "weekends":
                            scheduleDays.remove(scheduleDays.get(i));
                            addIfNotAdded(scheduleDays, "sunday");
                            addIfNotAdded(scheduleDays, "saturday");
                            break;
                        case "everyday":
                            scheduleDays.remove(scheduleDays.get(i));
                            addIfNotAdded(scheduleDays, "sunday");
                            addIfNotAdded(scheduleDays, "monday");
                            addIfNotAdded(scheduleDays, "tuesday");
                            addIfNotAdded(scheduleDays, "wednesday");
                            addIfNotAdded(scheduleDays, "thursday");
                            addIfNotAdded(scheduleDays, "friday");
                            addIfNotAdded(scheduleDays, "saturday");
                            break;
                    }
                }

                TemporalAccessor scheduleTime = DateTimeFormatter.ofPattern ("kk:mm", Locale.ENGLISH).parse((String) schedule.get("time"));

                for (int i = 0; i < scheduleDays.size(); i++) {
                    ZonedDateTime previousOccurrence = ZonedDateTime.now(timezone)
                        .with(TemporalAdjusters.previous(DayOfWeek.valueOf(scheduleDays.get(i).toUpperCase())))
                        .with(ChronoField.CLOCK_HOUR_OF_DAY, scheduleTime.get(ChronoField.CLOCK_HOUR_OF_DAY))
                        .with(ChronoField.MINUTE_OF_HOUR, scheduleTime.get(ChronoField.MINUTE_OF_HOUR))
                        .with(ChronoField.SECOND_OF_MINUTE, 0);

                    ZonedDateTime now = ZonedDateTime.now(timezone);
                    
                    ZonedDateTime nextOccurrence = ZonedDateTime.now(timezone)
                        .with(TemporalAdjusters.nextOrSame(DayOfWeek.valueOf(scheduleDays.get(i).toUpperCase())))
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
                for (int i = 0; i < scheduleDays.size(); i++) {
                    if (i != 0) {
                        scheduleMessage.append(", ");
                    }
                    scheduleMessage.append(scheduleDays.get(i).substring(0, 1).toUpperCase() + scheduleDays.get(i).substring(1));
                }
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

        /**
     * Adds a String to an ArrayList, if it doesn't already contain the String
     * @param list the ArrayList
     * @param item the String
     */
    private static void addIfNotAdded(ArrayList<String> list, String item) {
        if (!list.contains(item)) list.add(item);
    }
}

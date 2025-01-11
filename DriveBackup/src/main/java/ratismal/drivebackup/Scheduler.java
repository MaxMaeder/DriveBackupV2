package ratismal.drivebackup;

import org.jetbrains.annotations.Contract;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import ratismal.drivebackup.configuration.ConfigurationObject;
import ratismal.drivebackup.handler.logging.LoggingInterface;
import ratismal.drivebackup.handler.task.IndependentTaskHandler;
import ratismal.drivebackup.objects.BackupScheduleEntry;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Scheduler {
    
    /**
     * How often to run the schedule drift correction task, in seconds.
     */
    private static final long SCHEDULE_DRIFT_CORRECTION_INTERVAL = TimeUnit.DAYS.toSeconds(1L);

    /**
     * List of the IDs of the scheduled backup tasks
     */
    private static final List<ScheduledFuture<?>> backupTasks = new ArrayList<>(2);

    /**
     * ID of the schedule drift correction task
     */
    private static ScheduledFuture<?> scheduleDriftTask = null;

    /**
     * List of Dates representing each time a scheduled backup will occur.
     */
    private final List<ZonedDateTime> backupDatesList = new ArrayList<>(10);
    private final DriveBackupInstance instance;
    private List<BackupScheduleEntry> backupSchedule = new ArrayList<>();
    
    public Scheduler(DriveBackupInstance instance) {
        this.instance = instance;
    }
    
    private void cancelAllTasks() {
        for (ScheduledFuture<?> taskId : backupTasks) {
            taskId.cancel(false);
        }
    }
    
    private void loadBackupSchedule() {
        LoggingInterface logger = instance.getLoggingHandler().getPrefixedLogger("BackupScheduleLoader");
        ConfigurationObject configObject = instance.getConfigHandler().getConfig();
        CommentedConfigurationNode scheduleNode = configObject.getConfig().node("backup-scheduling");
        List<CommentedConfigurationNode> scheduleNodes = scheduleNode.childrenList();
        backupSchedule = new ArrayList<>(scheduleNodes.size());
        DateTimeFormatter timeFormatter = new DateTimeFormatterBuilder()
                .appendOptional(DateTimeFormatter.ofPattern("kk:mm"))
                .appendOptional(DateTimeFormatter.ofPattern("k:mm"))
                .toFormatter();
        for (CommentedConfigurationNode scheduleEntryNode : scheduleNodes) {
            List<String> rawDays;
            try {
                rawDays = scheduleEntryNode.node("days").getList(String.class);
            } catch (SerializationException e) {
                logger.error("Failed to load backup schedule entry: ", e);
                continue;
            }
            Set<DayOfWeek> days = new HashSet<>(rawDays.size());
            for (String rawDay : rawDays) {
                if (rawDay.equals("everyday")) {
                    days.addAll(Arrays.asList(DayOfWeek.values()));
                    continue;
                }
                if (rawDay.equals("weekdays")) {
                    days.add(DayOfWeek.MONDAY);
                    days.add(DayOfWeek.TUESDAY);
                    days.add(DayOfWeek.WEDNESDAY);
                    days.add(DayOfWeek.THURSDAY);
                    days.add(DayOfWeek.FRIDAY);
                    continue;
                }
                if (rawDay.equals("weekends")) {
                    days.add(DayOfWeek.SATURDAY);
                    days.add(DayOfWeek.SUNDAY);
                    continue;
                }
                try {
                    days.add(DayOfWeek.valueOf(rawDay.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    logger.error("Not a valid day of the week, " + rawDay + ";", e);
                }
            }
            if (days.isEmpty()) {
                logger.error("No valid days of the week found in backup schedule entry");
                continue;
            }
            Set<TemporalAccessor> times = new HashSet<>(2);
            if (scheduleEntryNode.node("time").isList()) {
                List<String> rawTime;
                try {
                    rawTime = scheduleEntryNode.node("time").getList(String.class);
                } catch (SerializationException e) {
                    logger.error("Failed to load backup schedule entry: ", e);
                    continue;
                }
                for (String rawTimeEntry : rawTime) {
                    try {
                        times.add(timeFormatter.parse(rawTimeEntry));
                    } catch (IllegalArgumentException e) {
                        logger.error("Not a valid time, " + rawTimeEntry + ";", e);
                    }
                }
            } else {
                String time = scheduleEntryNode.node("time").getString();
                try {
                    times.add(timeFormatter.parse(time));
                } catch (IllegalArgumentException e) {
                    logger.error("Not a valid time, " + time + ";", e);
                }
            }
            if (times.isEmpty()) {
                logger.error("No valid times found in backup schedule entry");
                continue;
            }
            backupSchedule.add(new BackupScheduleEntry(days.toArray(new DayOfWeek[0]),
                    times.toArray(new TemporalAccessor[0])));
        }
    }
    
    /**
     * Starts the backup thread
     */
    public void startBackupThread() {
        ConfigurationObject config1 = instance.getConfigHandler().getConfig();
        IndependentTaskHandler taskHandler = instance.getTaskHandler();
        cancelAllTasks();
        if (config1.getValue("scheduled-backups").getBoolean()) {
            if (backupSchedule.isEmpty()) {
                loadBackupSchedule();
            }
            backupDatesList.clear();
            String listDelimiter = instance.getMessageHandler().getLangString("list-delimiter");
            String lastListDelimiter = instance.getMessageHandler().getLangString("list-last-delimiter");
            Locale locale = new Locale(config1.getValue("advanced", "date-timezone").getString());
            for (BackupScheduleEntry entry : backupSchedule) {
                ZoneId timezone;
                String rawTimeZone = config1.getValue("advanced.date-timezone").getString();
                if (rawTimeZone.trim().isEmpty()) {
                    timezone = ZoneId.systemDefault();
                } else {
                    timezone = ZoneId.of(rawTimeZone);
                }
                for (DayOfWeek day : entry.days) {
                    for (TemporalAccessor time : entry.times) {
                        ZonedDateTime previousBackup = ZonedDateTime.now(timezone)
                                .with(TemporalAdjusters.previousOrSame(day))
                                .with(ChronoField.CLOCK_HOUR_OF_DAY, time.get(ChronoField.CLOCK_HOUR_OF_DAY))
                                .withMinute(time.get(ChronoField.MINUTE_OF_HOUR))
                                .with(ChronoField.SECOND_OF_MINUTE, 0L);
                        ZonedDateTime now = ZonedDateTime.now(timezone);
                        ZonedDateTime nextBackup = ZonedDateTime.now(timezone)
                                .with(TemporalAdjusters.nextOrSame(day))
                                .with(ChronoField.CLOCK_HOUR_OF_DAY, time.get(ChronoField.CLOCK_HOUR_OF_DAY))
                                .withMinute(time.get(ChronoField.MINUTE_OF_HOUR))
                                .with(ChronoField.SECOND_OF_MINUTE, 0L);
                        ZonedDateTime startingOccurrence = nextBackup;
                        if (now.isAfter(startingOccurrence)) {
                            startingOccurrence = startingOccurrence.plusWeeks(1L);
                        }
                        long delay = ChronoUnit.SECONDS.between(now, startingOccurrence);
                        long period = ChronoUnit.SECONDS.between(previousBackup, nextBackup);
                        ScheduledFuture<?> task = taskHandler.scheduleRepeatingTask(delay, period, TimeUnit.SECONDS, () -> {
                            new UploadThread(instance);
                        });
                        backupTasks.add(task);
                        backupDatesList.add(startingOccurrence);
                    }
                }
                StringBuilder scheduleTimes = new StringBuilder(100);
                for (int i = 0; i < entry.times.length; i++) {
                    if (i == entry.times.length - 1 && entry.times.length > 1) {
                        scheduleTimes.append(lastListDelimiter);
                    } else if (i != 0) {
                        scheduleTimes.append(listDelimiter);
                    }
                    TemporalAccessor time = entry.times[i];
                    ZonedDateTime scheduleMessageTime = ZonedDateTime.now(timezone)
                        .with(ChronoField.CLOCK_HOUR_OF_DAY, time.get(ChronoField.CLOCK_HOUR_OF_DAY))
                        .withMinute(time.get(ChronoField.MINUTE_OF_HOUR));
                    scheduleTimes.append(scheduleMessageTime.format(DateTimeFormatter.ofPattern("hh:mm a")));
                }
                StringBuilder scheduleDays = new StringBuilder(100);
                for (int i = 0; i < entry.days.length; i++) {
                    if (i == entry.days.length - 1 && entry.days.length > 1) {
                        scheduleDays.append(lastListDelimiter);
                    } else if (i != 0) {
                        scheduleDays.append(listDelimiter);
                    }
                    DayOfWeek day = entry.days[i];
                    scheduleDays.append(day.getDisplayName(TextStyle.FULL, locale));
                }
                Map<String, String> placeholders = new HashMap<>(2);
                placeholders.put("days", scheduleDays.toString());
                placeholders.put("time", scheduleTimes.toString());
                instance.getMessageHandler().Builder().toConsole()
                        .getLang("scheduled-backup-scheduled", placeholders).send();
            }
            if (scheduleDriftTask != null) {
                scheduleDriftTask.cancel(false);
            }
            scheduleDriftTask = taskHandler.scheduleRepeatingTask(
                    SCHEDULE_DRIFT_CORRECTION_INTERVAL,
                    SCHEDULE_DRIFT_CORRECTION_INTERVAL,
                    TimeUnit.SECONDS,
                    this::startBackupThread);
        } else if (config1.getValue("delay").getLong() != -1L) {
            if (scheduleDriftTask != null) {
                scheduleDriftTask.cancel(false);
            }
            long delayMinutes = config1.getValue("delay").getLong();
            instance.getMessageHandler().Builder().toConsole()
                    .getLang("backups-interval-scheduled", "delay",
                            String.valueOf(delayMinutes)).send();
            backupTasks.add(taskHandler.scheduleRepeatingTask(
                    delayMinutes,
                    delayMinutes,
                    TimeUnit.MINUTES,
                    () -> new UploadThread(instance)));
            UploadThread.updateNextIntervalBackupTime(instance);
        }
    }

    /**
     * Stops the backup thread
     */
    public void stopBackupThread() {
        cancelAllTasks();
    }

    /**
     * Gets a list of Dates representing each time a scheduled backup will occur.
     * @return the ArrayList of {@code ZonedDateTime} objects
     */
    @Contract (pure = true)
    public List<ZonedDateTime> getBackupDatesList() {
        return new ArrayList<>(backupDatesList);
    }
    
}

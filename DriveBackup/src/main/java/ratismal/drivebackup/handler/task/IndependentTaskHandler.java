package ratismal.drivebackup.handler.task;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.handler.logging.LoggingHandler;
import ratismal.drivebackup.handler.logging.PrefixedLogger;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * A platform independent task handler for scheduling tasks.
 */
public final class IndependentTaskHandler {
    
    private static final long TIMEOUT = 10L;
    private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MINUTES;
    private static final long KEEP_ALIVE_TIME = 60L;
    private static final TimeUnit KEEP_ALIVE_UNIT = TimeUnit.SECONDS;
    private static final int MAXIMUM_POOL_SIZE = 100;
    private static final int CORE_POOL_SIZE = 0;
    
    private ScheduledExecutorService scheduledExecutor;
    private final PrefixedLogger logger;
    private ThreadFactory threadFactory;
    
    /**
     * Creates a new IndependentTaskHandler instance.
     * After constructing, call {@link #setup()} to initialize the handler.
     * @param loggingHandler the logging handler to use
     */
    @Contract (pure = true)
    public IndependentTaskHandler(@NotNull LoggingHandler loggingHandler) {
        logger = loggingHandler.getPrefixedLogger("IndependentTaskHandler");
    }
    
    /**
     * Initializes the task handler.
     */
    public void setup() {
        threadFactory = new SimpleThreadFactory();
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(CORE_POOL_SIZE, threadFactory);
        scheduledThreadPoolExecutor.setMaximumPoolSize(MAXIMUM_POOL_SIZE);
        scheduledThreadPoolExecutor.setKeepAliveTime(KEEP_ALIVE_TIME, KEEP_ALIVE_UNIT);
        scheduledExecutor = scheduledThreadPoolExecutor;
    }
    
    /**
     * Shuts down the task handler.
     * Waits for tasks to finish for up to 10 minutes.
     * If tasks do not finish in time, forces shutdown.
     */
    public void shutdown() {
        shutdown(TIMEOUT, TIMEOUT_UNIT);
    }
    
    /**
     * Shuts down the task handler.
     * Waits for tasks to finish for up to timeout + unit time.
     * If tasks do not finish in time, forces shutdown.
     */
    public void shutdown(long timeout, TimeUnit unit){
        logger.info("Shutting down IndependentTaskHandler");
        logger.info("Shutting down scheduled executor");
        scheduledExecutor.shutdown();
        logger.info("Waiting for tasks to finish up to " + timeout + " " + unit.toString() + "...");
        try {
            boolean sch = scheduledExecutor.awaitTermination(timeout, unit);
            if (sch) {
                logger.info("Scheduled tasks finished");
            } else {
                logger.error("Scheduled tasks did not finish in time, forcing shutdown");
                scheduledExecutor.shutdownNow();
                logger.info("Forced scheduled executor shutdown");
            }
            logger.info("IndependentTaskHandler shutdown complete");
        } catch (InterruptedException e) {
            logger.error("Was interrupted while waiting for tasks to finish: ", e);
        }
    }
    
    /**
     * Schedules a repeating task.
     * The task will run after the delay and then every period.
     * <p>
     * see {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)}
     *
     * @param delay the delay before the first run
     * @param period the period between runs
     * @param unit the time unit of the delay and period
     * @param runnable the task to run
     * @return a ScheduledFuture representing the task
     */
    public @NotNull ScheduledFuture<?> scheduleRepeatingTask(long delay, long period, TimeUnit unit, Runnable runnable) {
        return scheduledExecutor.scheduleAtFixedRate(runnable, delay, period, unit);
    }
    
    /**
     * Schedules a delayed task.
     * The task will run after the delay.
     * <p>
     * see {@link ScheduledExecutorService#schedule(Runnable, long, TimeUnit)}
     *
     * @param delay the delay before the task runs
     * @param unit the time unit of the delay
     * @param runnable the task to run
     * @return a ScheduledFuture representing the task
     */
    public @NotNull ScheduledFuture<?> scheduleLaterTask(long delay, TimeUnit unit, Runnable runnable) {
        return scheduledExecutor.schedule(runnable, delay, unit);
    }
    
    /**
     * Schedules a delayed task.
     * The task will run after the delay.
     * <p>
     * see {@link ScheduledExecutorService#schedule(Callable, long, TimeUnit)}
     *
     * @param <T> the type of the result
     * @param delay the delay
     * @param unit the time unit of the delay
     * @param callable the task to run
     * @return a ScheduledFuture representing the task
     */
    public <T> @NotNull ScheduledFuture<T> scheduleLaterTask(long delay, TimeUnit unit, Callable<T> callable) {
        return scheduledExecutor.schedule(callable, delay, unit);
    }
    
}

package ratismal.drivebackup.handler.task;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public interface TaskHandler {
    
    <T> Future<T> callSyncMethod(Callable<T> task);
    
    /**
     * Schedules a repeating task to run synchronously with the server
     * @param runnable the task to run
     * @param delay the delay before the task starts
     * @param period the period between each run
     * @param unit the unit of time for the delay and period
     */
    void scheduleSyncRepeatingTask(Runnable runnable, long delay, long period, TimeUnit unit);
    
    /**
     * Schedules a repeating task to run asynchronously with the server
     * @param runnable the task to run
     * @param delay the delay before the task starts
     * @param period the period between each run
     * @param unit the unit of time for the delay and period
     */
    void scheduleAsyncRepeatingTask(Runnable runnable, long delay, long period, TimeUnit unit);
    
    /**
     * Schedules a task to run synchronously with the server
     * @param runnable the task to run
     * @param delay the delay before the task starts
     * @param unit the unit of time for the delay
     */
    void scheduleSyncDelayedTask(Runnable runnable, long delay, TimeUnit unit);
    
    /**
     * Schedules a task to run asynchronously with the server
     * @param runnable the task to run
     * @param delay the delay before the task starts
     * @param unit the unit of time for the delay
     */
    void scheduleAsyncDelayedTask(Runnable runnable, long delay, TimeUnit unit);
    
    void cancelAllTasks();
}

package ratismal.drivebackup.platforms.bukkit;

import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.handler.task.TaskHandler;
import ratismal.drivebackup.handler.task.TaskIdentifier;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class BukkitTaskHandler implements TaskHandler {
    
    private final BukkitPlugin instance;
    private final BukkitScheduler scheduler;
    
    @Contract (pure = true)
    public BukkitTaskHandler(BukkitPlugin instance) {
        this.instance = instance;
        scheduler = instance.getServer().getScheduler();
    }
    
    @Override
    public @NotNull TaskIdentifier scheduleSyncRepeatingTask(Runnable runnable, long delay, long period, @NotNull TimeUnit unit) {
        BukkitTask task = scheduler.runTaskTimer(instance, runnable, unit.toSeconds(delay), unit.toSeconds(period));
        return new TaskIdentifier(task.getTaskId());
    }
    
    @Override
    public @NotNull TaskIdentifier scheduleAsyncRepeatingTask(Runnable runnable, long delay, long period, @NotNull TimeUnit unit) {
        BukkitTask task = scheduler.runTaskTimerAsynchronously(instance, runnable, unit.toSeconds(delay), unit.toSeconds(period));
        return new TaskIdentifier(task.getTaskId());
    }
    
    @Override
    public @NotNull TaskIdentifier scheduleSyncDelayedTask(Runnable runnable, long delay, @NotNull TimeUnit unit) {
        BukkitTask task = scheduler.runTaskLater(instance, runnable, unit.toSeconds(delay));
        return new TaskIdentifier(task.getTaskId());
    }
    
    @Override
    public @NotNull TaskIdentifier scheduleAsyncDelayedTask(Runnable runnable, long delay, @NotNull TimeUnit unit) {
        BukkitTask task = scheduler.runTaskLaterAsynchronously(instance, runnable, unit.toSeconds(delay));
        return new TaskIdentifier(task.getTaskId());
    }
    
    @Override
    public void cancelTask(TaskIdentifier taskId) {
        scheduler.cancelTask(taskId.getTaskId());
    }
    
    @Override
    public void cancelAllTasks() {
        scheduler.cancelTasks(instance);
    }
    
    
    @Override
    public <T> Future<T> callSyncMethod(Callable<T> task) {
        return scheduler.callSyncMethod(instance, task);
    }
    
}

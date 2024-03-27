package ratismal.drivebackup.platforms.bukkit;

import org.jetbrains.annotations.Contract;
import ratismal.drivebackup.handler.task.TaskHandler;

import java.util.concurrent.TimeUnit;

public final class BukkitTaskHandler implements TaskHandler {
    
    private final BukkitPlugin instance;
    
    @Contract (pure = true)
    public BukkitTaskHandler(BukkitPlugin instance) {
        this.instance = instance;
    }
    
    @Override
    public void scheduleSyncRepeatingTask(Runnable runnable, long delay, long period, TimeUnit unit) {
        instance.getServer().getScheduler().runTaskTimer(instance, runnable, unit.toSeconds(delay), unit.toSeconds(period));
    
    }
    
    @Override
    public void scheduleAsyncRepeatingTask(Runnable runnable, long delay, long period, TimeUnit unit) {
        instance.getServer().getScheduler().runTaskTimerAsynchronously(instance, runnable, unit.toSeconds(delay), unit.toSeconds(period));
    }
    
    @Override
    public void scheduleSyncDelayedTask(Runnable runnable, long delay, TimeUnit unit) {
        instance.getServer().getScheduler().runTaskLater(instance, runnable, unit.toSeconds(delay));
    }
    
    @Override
    public void scheduleAsyncDelayedTask(Runnable runnable, long delay, TimeUnit unit) {
        instance.getServer().getScheduler().runTaskLaterAsynchronously(instance, runnable, unit.toSeconds(delay));
    }
    
    @Override
    public void cancelAllTasks() {
        instance.getServer().getScheduler().cancelTasks(instance);
    }
    
}

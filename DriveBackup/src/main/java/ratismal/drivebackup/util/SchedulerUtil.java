package ratismal.drivebackup.util;

import java.util.ArrayList;

import org.bukkit.Bukkit;

public class SchedulerUtil {
    private static final int TICKS_PER_SECOND = 20;

    /**
     * Cancels the specified tasks
     * @param taskList an ArrayList of the IDs of the tasks
     */
    public static void cancelTasks(ArrayList<Integer> taskList) {
        for (int i = 0; i < taskList.size(); i++) {
            Bukkit.getScheduler().cancelTask(taskList.get(i));
            taskList.remove(i);
        }
    }

    /**
     * Converts the specified number of seconds to game ticks
     * @param seconds the number of seconds
     * @return the number of game ticks
     */
    public static long sToTicks(double seconds) {
        return (long) seconds * TICKS_PER_SECOND;
    }
}

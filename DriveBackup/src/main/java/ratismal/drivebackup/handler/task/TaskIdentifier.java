package ratismal.drivebackup.handler.task;

import org.jetbrains.annotations.Contract;

public final class TaskIdentifier {
    
    private final int taskId;
    
    @Contract (pure = true)
    public TaskIdentifier(int taskId) {
        this.taskId = taskId;
    }
    
    @Contract (pure = true)
    public int getTaskId() {
        return taskId;
    }
}

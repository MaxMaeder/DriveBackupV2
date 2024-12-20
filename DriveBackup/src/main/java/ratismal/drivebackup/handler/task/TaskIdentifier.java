package ratismal.drivebackup.handler.task;

import lombok.Getter;
import org.jetbrains.annotations.Contract;

@Getter
public final class TaskIdentifier {
    
    private final int taskId;
    
    @Contract (pure = true)
    public TaskIdentifier(int taskId) {
        this.taskId = taskId;
    }
    
}

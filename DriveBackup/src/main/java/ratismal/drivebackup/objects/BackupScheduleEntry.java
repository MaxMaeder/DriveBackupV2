package ratismal.drivebackup.objects;

import org.jetbrains.annotations.Contract;

import java.time.DayOfWeek;
import java.time.temporal.TemporalAccessor;

public class BackupScheduleEntry {
    
    public final DayOfWeek[] days;
    public final TemporalAccessor[] times;
    
    @Contract (pure = true)
    public BackupScheduleEntry(DayOfWeek[] days, TemporalAccessor[] times) {
        this.days = days;
        this.times = times;
    }
    
}

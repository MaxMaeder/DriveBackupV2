package ratismal.drivebackup.objects;

import org.jetbrains.annotations.Contract;
import ratismal.drivebackup.util.LocalDateTimeFormatter;

public class BackupListEntry {
    
    public final BackupLocation location;
    public final LocalDateTimeFormatter formatter;
    public final boolean create;
    public final String[] blacklist;
    
    @Contract (pure = true)
    public BackupListEntry(
            BackupLocation location,
            LocalDateTimeFormatter formatter,
            boolean create,
            String[] blacklist) {
        this.location = location;
        this.formatter = formatter;
        this.create = create;
        this.blacklist = blacklist;
    }
    
}

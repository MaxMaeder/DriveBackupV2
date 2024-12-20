package ratismal.drivebackup.objects;

import org.jetbrains.annotations.Contract;

public final class ExternalBackupListEntry {
    
    public final String path;
    public final String[] blacklist;
    
    @Contract (pure = true)
    public ExternalBackupListEntry(String path, String[] blacklist) {
        this.path = path;
        this.blacklist = blacklist;
    }
    
}

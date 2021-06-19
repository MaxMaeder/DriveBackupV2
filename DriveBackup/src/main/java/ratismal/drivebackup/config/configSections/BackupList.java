package ratismal.drivebackup.config.configSections;

import java.nio.file.Path;
import java.time.ZoneOffset;

public class BackupList {
    public static class BackupListEntry {
        public final Path[] paths;
        public final String format;
        public final boolean create;
        public final String[] blacklist;
        
        public BackupListEntry(
            Path[] paths, 
            String format, 
            boolean create, 
            String[] blacklist
            ) {

            this.paths = paths;
            this.format = format;
            this.create = create;
            this.blacklist = blacklist;
        }
    }

    public final ZoneOffset backupFormatTimezone;
    public final BackupListEntry[] backupListEntries;

    public BackupList(
        ZoneOffset backupFormatTimezone, 
        BackupListEntry[] backupListEntries
        ) {

        this.backupFormatTimezone = backupFormatTimezone;
        this.backupListEntries = backupListEntries;
    }
}
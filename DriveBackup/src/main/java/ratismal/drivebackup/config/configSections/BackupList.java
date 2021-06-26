package ratismal.drivebackup.config.configSections;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.ConfigParser.Logger;
import ratismal.drivebackup.config.configSections.BackupList.BackupListEntry.BackupLocation;
import ratismal.drivebackup.util.FileUtil;
import ratismal.drivebackup.util.LocalDateTimeFormatter;

public class BackupList {
    public static class BackupListEntry {
        public interface BackupLocation {
            public List<Path> getPaths();
        }

        public static class PathBackupLocation implements BackupLocation {
            private final Path path;

            private PathBackupLocation(String path) {
                this.path = Path.of(path);
            }

            public List<Path> getPaths() {
                return Collections.singletonList(path);
            }
        }

        public static class GlobBackupLocation implements BackupLocation {
            private final String glob;

            private GlobBackupLocation(String glob) {
                this.glob = glob;
            }

            public List<Path> getPaths() {
                return FileUtil.generateGlobFolderList(glob, ".");
            }
        }

        public final BackupLocation location;
        public final LocalDateTimeFormatter formatter;
        public final boolean create;
        public final String[] blacklist;
        
        private BackupListEntry(
            BackupLocation location,
            LocalDateTimeFormatter formatter, 
            boolean create, 
            String[] blacklist
            ) {

            this.location = location;
            this.formatter = formatter;
            this.create = create;
            this.blacklist = blacklist;
        }
    }

    public final BackupListEntry[] list;

    private BackupList(
        BackupListEntry[] list
        ) {

        this.list = list;
    }

    public static BackupList parse(FileConfiguration config, Logger logger) {
        List<Map<?, ?>> rawList = config.getMapList("backup-list");
        ArrayList<BackupListEntry> list = new ArrayList<>();
        for (Map<?, ?> rawListEntry : rawList) {
            int entryIndex = rawList.indexOf(rawListEntry) + 1;

            BackupLocation location;
            if (rawListEntry.containsKey("glob")) {
                try {
                    location = new BackupListEntry.GlobBackupLocation((String) rawListEntry.get("glob"));
                } catch (Exception e) {
                    logger.log("Glob invalid, skipping backup list entry " + entryIndex);
                    continue;
                }
            } else if (rawListEntry.containsKey("path")) {
                try {
                    location = new BackupListEntry.PathBackupLocation((String) rawListEntry.get("path"));
                } catch (Exception e) {
                    logger.log("Path invalid, skipping backup list entry " + entryIndex);
                    continue;
                }
            } else {
                logger.log("No path or glob specified, skipping backup list entry " + entryIndex);
                continue;
            }

            LocalDateTimeFormatter formatter;
            try {
                formatter = LocalDateTimeFormatter.ofPattern((String) rawListEntry.get("format"));
            } catch (Exception e) {
                logger.log("Format invalid, skipping backup list entry " + entryIndex);
                continue;
            }

            boolean create = true;
            try {
                create = (boolean) rawListEntry.get("create");
            } catch (Exception e) { 
                // Do nothing, assume true
            }

            String[] blacklist;
            try {
                blacklist = (String[]) ((List<String>) rawListEntry.get("blacklist")).toArray();
            } catch (Exception e) {
                // Do nothing, blacklist not required
                blacklist = new String[0];
            }

            
            list.add(new BackupListEntry(location, formatter, create, (String[]) blacklist));
        }

        return new BackupList((BackupListEntry[]) list.toArray());
    }
}
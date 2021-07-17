package ratismal.drivebackup.config.configSections;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.util.Logger;
import ratismal.drivebackup.config.configSections.BackupList.BackupListEntry.BackupLocation;
import ratismal.drivebackup.util.FileUtil;
import ratismal.drivebackup.util.LocalDateTimeFormatter;

import static ratismal.drivebackup.config.Localization.intl;

public class BackupList {
    public static class BackupListEntry {
        public interface BackupLocation {
            public List<Path> getPaths();
            public String toString();
        }

        public static class PathBackupLocation implements BackupLocation {
            private final Path path;

            public PathBackupLocation(String path) {
                this.path = Paths.get(path);
            }

            public List<Path> getPaths() {
                return Collections.singletonList(path);
            }

            public String toString() {
                return path.toString();
            }
        }

        public static class GlobBackupLocation implements BackupLocation {
            private final String glob;

            public GlobBackupLocation(String glob) {
                this.glob = glob;
            }

            public List<Path> getPaths() {
                return FileUtil.generateGlobFolderList(glob, ".");
            }

            public String toString() {
                return glob;
            }
        }

        public final BackupLocation location;
        public final LocalDateTimeFormatter formatter;
        public final boolean create;
        public final String[] blacklist;
        
        public BackupListEntry(
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

    public BackupList(
        BackupListEntry[] list
        ) {

        this.list = list;
    }

    public static BackupList parse(FileConfiguration config, Logger logger) {
        List<Map<?, ?>> rawList = config.getMapList("backup-list");
        ArrayList<BackupListEntry> list = new ArrayList<>();
        for (Map<?, ?> rawListEntry : rawList) {
            String entryIndex = String.valueOf(rawList.indexOf(rawListEntry) + 1);

            BackupLocation location;
            if (rawListEntry.containsKey("glob")) {
                try {
                    location = new BackupListEntry.GlobBackupLocation((String) rawListEntry.get("glob"));
                } catch (Exception e) {
                    logger.log(intl("backup-list-glob-invalid"), "entry", entryIndex);
                    continue;
                }
            } else if (rawListEntry.containsKey("path")) {
                try {
                    location = new BackupListEntry.PathBackupLocation((String) rawListEntry.get("path"));
                } catch (Exception e) {
                    logger.log(intl("backup-list-path-invalid"), "entry", entryIndex);
                    continue;
                }
            } else {
                logger.log(intl("backup-list-no-dest-specified"), "entry", entryIndex);
                continue;
            }

            LocalDateTimeFormatter formatter;
            try {
                formatter = LocalDateTimeFormatter.ofPattern((String) rawListEntry.get("format"));
            } catch (Exception e) {
                logger.log(intl("backup-list-format-invalid"), "entry", entryIndex);
                if (e instanceof IllegalArgumentException) e.getMessage();
                continue;
            }

            boolean create = true;
            try {
                create = (boolean) (Boolean) rawListEntry.get("create");
            } catch (Exception e) { 
                // Do nothing, assume true
            }

            String[] blacklist = new String[0];
            if (rawListEntry.containsKey("blacklist")) {
                try {
                    blacklist = ((List<String>) rawListEntry.get("blacklist")).toArray(new String[0]);
                } catch (Exception e) {
                    logger.log(intl("backup-list-blacklist-invalid"), "entry", entryIndex);
                }
            }
            
            list.add(new BackupListEntry(location, formatter, create, blacklist));
        }

        return new BackupList(list.toArray(new BackupListEntry[0]));
    }
}
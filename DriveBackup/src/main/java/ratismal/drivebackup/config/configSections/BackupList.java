package ratismal.drivebackup.config.configSections;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.util.Logger;
import ratismal.drivebackup.config.configSections.BackupList.BackupListEntry.BackupLocation;
import ratismal.drivebackup.util.FileUtil;
import ratismal.drivebackup.util.LocalDateTimeFormatter;

import static ratismal.drivebackup.config.Localization.intl;

public class BackupList {
    
    public static final String ENTRY = "entry";
    
    public static class BackupListEntry {
        public interface BackupLocation {
            List<Path> getPaths();
            String toString();
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

    @NotNull
    @Contract ("_, _ -> new")
    public static BackupList parse(@NotNull FileConfiguration config, Logger logger) {
        List<Map<?, ?>> rawList = config.getMapList("backup-list");
        ArrayList<BackupListEntry> list = new ArrayList<>();
        for (Map<?, ?> rawListEntry : rawList) {
            String entryIndex = String.valueOf(rawList.indexOf(rawListEntry) + 1);
            BackupLocation location;
            if (rawListEntry.containsKey("glob")) {
                try {
                    location = new BackupListEntry.GlobBackupLocation((String) rawListEntry.get("glob"));
                } catch (ClassCastException e) {
                    logger.log(intl("backup-list-glob-invalid"), ENTRY, entryIndex);
                    continue;
                }
            } else if (rawListEntry.containsKey("path")) {
                try {
                    location = new BackupListEntry.PathBackupLocation((String) rawListEntry.get("path"));
                } catch (ClassCastException e) {
                    logger.log(intl("backup-list-path-invalid"), ENTRY, entryIndex);
                    continue;
                }
            } else {
                logger.log(intl("backup-list-no-dest-specified"), ENTRY, entryIndex);
                continue;
            }
            LocalDateTimeFormatter formatter;
            try {
                formatter = LocalDateTimeFormatter.ofPattern((String) rawListEntry.get("format"));
            } catch (IllegalArgumentException | ClassCastException e) {
                logger.log(intl("backup-list-format-invalid"), ENTRY, entryIndex);
                continue;
            }
            boolean create = true;
            try {
                create = (Boolean) rawListEntry.get("create");
            } catch (ClassCastException e) {
                // Do nothing, assume true
            }
            String[] blacklist = new String[0];
            if (rawListEntry.containsKey("blacklist")) {
                try {
                    blacklist = ((List<String>) rawListEntry.get("blacklist")).toArray(new String[0]);
                } catch (ClassCastException | ArrayStoreException e) {
                    logger.log(intl("backup-list-blacklist-invalid"), ENTRY, entryIndex);
                }
            }
            list.add(new BackupListEntry(location, formatter, create, blacklist));
        }
        return new BackupList(list.toArray(new BackupListEntry[0]));
    }
}

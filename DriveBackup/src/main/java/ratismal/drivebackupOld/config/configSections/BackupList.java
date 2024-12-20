package ratismal.drivebackupOld.config.configSections;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.objects.BackupListEntry;
import ratismal.drivebackup.objects.BackupLocation;
import ratismal.drivebackup.objects.GlobBackupLocation;
import ratismal.drivebackup.objects.PathBackupLocation;
import ratismal.drivebackup.util.LocalDateTimeFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Deprecated
public class BackupList {
    
    public static final String ENTRY = "entry";
    public final BackupListEntry[] list;

    @Contract (pure = true)
    public BackupList(
        BackupListEntry[] list
        ) {

        this.list = list;
    }

    @NotNull
    @Contract ("_ -> new")
    public static BackupList parse(@NotNull FileConfiguration config) {
        List<Map<?, ?>> rawList = config.getMapList("backup-list");
        ArrayList<BackupListEntry> list = new ArrayList<>();
        for (Map<?, ?> rawListEntry : rawList) {
            String entryIndex = String.valueOf(rawList.indexOf(rawListEntry) + 1);
            BackupLocation location;
            if (rawListEntry.containsKey("glob")) {
                try {
                    location = new GlobBackupLocation((String) rawListEntry.get("glob"));
                } catch (ClassCastException e) {
                    continue;
                }
            } else if (rawListEntry.containsKey("path")) {
                try {
                    location = new PathBackupLocation((String) rawListEntry.get("path"));
                } catch (ClassCastException e) {
                    continue;
                }
            } else {
                continue;
            }
            LocalDateTimeFormatter formatter;
            try {
                formatter = LocalDateTimeFormatter.ofPattern(null,(String) rawListEntry.get("format"));
            } catch (IllegalArgumentException | ClassCastException e) {
                continue;
            }
            boolean create = true;
            try {
                create = (Boolean) rawListEntry.get("create");
            } catch (ClassCastException | NullPointerException e) {
                // Do nothing, assume true
            }
            String[] blacklist = new String[0];
            if (rawListEntry.containsKey("blacklist")) {
                try {
                    blacklist = ((List<String>) rawListEntry.get("blacklist")).toArray(new String[0]);
                } catch (ClassCastException | ArrayStoreException ignored) {
                }
            }
            list.add(new BackupListEntry(location, formatter, create, blacklist));
        }
        return new BackupList(list.toArray(new BackupListEntry[0]));
    }
    
}

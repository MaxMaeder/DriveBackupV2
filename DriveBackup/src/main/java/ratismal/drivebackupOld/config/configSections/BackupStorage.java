package ratismal.drivebackupOld.config.configSections;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.zip.Deflater;

@Deprecated
public class BackupStorage {
    public final long delay;
    public final int threadPriority;
    public final int keepCount;
    public final int localKeepCount;
    public final int zipCompression;
    public final boolean backupsRequirePlayers;
    public final boolean disableSavingDuringBackups;
    public final String localDirectory;
    public final String remoteDirectory;

    @Contract (pure = true)
    public BackupStorage(
        long delay, 
        int threadPriority, 
        int keepCount, 
        int localKeepCount,
        int zipCompression,
        boolean backupsRequirePlayers,
        boolean disableSavingDuringBackups,
        String localDirectory,
        String remoteDirectory
        ) {

        this.delay = delay;
        this.threadPriority = threadPriority;
        this.keepCount = keepCount;
        this.localKeepCount = localKeepCount;
        this.zipCompression = zipCompression;
        this.backupsRequirePlayers = backupsRequirePlayers;
        this.disableSavingDuringBackups = disableSavingDuringBackups;
        this.localDirectory = localDirectory;
        this.remoteDirectory = remoteDirectory;
    }

    @NotNull
    @Contract ("_ -> new")
    public static BackupStorage parse(@NotNull FileConfiguration config) {
        Configuration defaultConfig = config.getDefaults();
        long delay = config.getLong("delay");
        if (delay < 5L && delay != -1L) {
            delay = defaultConfig.getLong("delay");
        }
        int threadPriority = config.getInt("backup-thread-priority");
        if (threadPriority < Thread.MIN_PRIORITY) {
            threadPriority = Thread.MIN_PRIORITY;
        } else if (threadPriority > Thread.MAX_PRIORITY) {
            threadPriority = Thread.MAX_PRIORITY;
        }
        int keepCount = config.getInt("keep-count");
        if (keepCount < 1 && keepCount != -1) {
            keepCount = defaultConfig.getInt("keep-count");
        }
        int localKeepCount = config.getInt("local-keep-count");
        if (localKeepCount < -1) {
            localKeepCount = defaultConfig.getInt("local-keep-count");
        }
        int zipCompression = config.getInt("zip-compression");
        if (zipCompression < Deflater.BEST_SPEED) {
            zipCompression = Deflater.BEST_SPEED;
        } else if (zipCompression > Deflater.BEST_COMPRESSION) {
            zipCompression = Deflater.BEST_COMPRESSION;
        }
        boolean backupsRequirePlayers = config.getBoolean("backups-require-players");
        boolean disableSavingDuringBackups = config.getBoolean("disable-saving-during-backups");
        String localDirectory = config.getString("local-save-directory");
        if (localDirectory.startsWith("/")) {
            localDirectory = localDirectory.substring(1);
        }
        String remoteDirectory = config.getString("remote-save-directory");
        return new BackupStorage(delay, threadPriority, keepCount, localKeepCount, zipCompression, backupsRequirePlayers, disableSavingDuringBackups, localDirectory, remoteDirectory);
    }
}

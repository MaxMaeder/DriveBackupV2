package ratismal.drivebackup.config.configSections;

import java.util.zip.Deflater;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.util.Logger;

import static ratismal.drivebackup.config.Localization.intl;

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
    @Contract ("_, _ -> new")
    public static BackupStorage parse(@NotNull FileConfiguration config, Logger logger) {
        Configuration defaultConfig = config.getDefaults();
        long delay = config.getLong("delay");
        if (delay < 5 && delay != -1) {
            logger.log(intl("invalid-backup-delay"));
            delay = defaultConfig.getLong("delay");
        }
        int threadPriority = config.getInt("backup-thread-priority");
        if (threadPriority < Thread.MIN_PRIORITY) {
            logger.log(intl("thread-priority-too-low"));
            threadPriority = Thread.MIN_PRIORITY;
        } else if (threadPriority > Thread.MAX_PRIORITY) {
            logger.log(intl("thread-priority-too-high"));
            threadPriority = Thread.MAX_PRIORITY;
        }
        int keepCount = config.getInt("keep-count");
        if (keepCount < 1 && keepCount != -1) {
            logger.log(intl("keep-count-invalid"));
            keepCount = defaultConfig.getInt("keep-count");
        }
        int localKeepCount = config.getInt("local-keep-count");
        if (localKeepCount < -1) {
            logger.log(intl("local-keep-count-invalid"));
            localKeepCount = defaultConfig.getInt("local-keep-count");
        }
        int zipCompression = config.getInt("zip-compression");
        if (zipCompression < Deflater.BEST_SPEED) {
            logger.log(intl("zip-compression-too-low"));
            zipCompression = Deflater.BEST_SPEED;
        } else if (zipCompression > Deflater.BEST_COMPRESSION) {
            logger.log(intl("zip-compression-too-high"));
            zipCompression = Deflater.BEST_COMPRESSION;
        }
        boolean backupsRequirePlayers = config.getBoolean("backups-require-players");
        boolean disableSavingDuringBackups = config.getBoolean("disable-saving-during-backups");
        String localDirectory = config.getString("local-save-directory");
        String remoteDirectory = config.getString("remote-save-directory");
        return new BackupStorage(delay, threadPriority, keepCount, localKeepCount, zipCompression, backupsRequirePlayers, disableSavingDuringBackups, localDirectory, remoteDirectory);
    }
}

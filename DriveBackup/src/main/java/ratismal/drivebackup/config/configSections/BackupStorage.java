package ratismal.drivebackup.config.configSections;

import java.util.zip.Deflater;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.ConfigParser.Logger;

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

    public static BackupStorage parse(FileConfiguration config, Logger logger) {
        Configuration defaultConfig = config.getDefaults();

        long delay = config.getLong("delay");
        if (delay <= 5) {
            logger.log("Inputted backup delay invalid, using default");
            delay = defaultConfig.getLong("delay");
        }

        int threadPriority = config.getInt("backup-thread-priority");
        if (threadPriority < Thread.MIN_PRIORITY) {
            logger.log("Inputted thread priority less than minimum, using minimum");
            threadPriority = Thread.MIN_PRIORITY;
        } else if (threadPriority > Thread.MAX_PRIORITY) {
            logger.log("Inputted thread priority more than maximum, using maximum");
            threadPriority = Thread.MAX_PRIORITY;
        }

        int keepCount = config.getInt("keep-count");
        if (keepCount < -1) {
            logger.log("Keep count invalid, using default");
            keepCount = defaultConfig.getInt("keep-count");
        }

        int localKeepCount = config.getInt("local-keep-count");
        if (localKeepCount < -1) {
            logger.log("Inputted local keep count invalid, using default");
            localKeepCount = defaultConfig.getInt("local-keep-count");
        }

        int zipCompression = config.getInt("zip-compression");
        if (zipCompression < Deflater.BEST_SPEED) {
            logger.log("Inputted zip compression less than minimum, using minimum");
            zipCompression = Deflater.BEST_SPEED;
        } else if (zipCompression > Deflater.BEST_COMPRESSION) {
            logger.log("Inputted zip compression more than maximum, using maximum");
            zipCompression = Deflater.BEST_COMPRESSION;
        }

        boolean backupsRequirePlayers = config.getBoolean("backups-require-players");
        boolean disableSavingDuringBackups = config.getBoolean("disable-saving-during-backups");

        String localDirectory = config.getString("dir");
        String remoteDirectory = config.getString("directory");

        return new BackupStorage(delay, threadPriority, keepCount, localKeepCount, zipCompression, backupsRequirePlayers, disableSavingDuringBackups, localDirectory, remoteDirectory);
    }
} 
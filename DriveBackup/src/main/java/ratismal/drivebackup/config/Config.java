package ratismal.drivebackup.config;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

public class Config {
    public static class BackupStorage {
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
    } 

    public static class BackupScheduling {
        public static class BackupScheduleEntry {
            public final DayOfWeek[] days;
            public final TemporalAccessor time;

            public BackupScheduleEntry(DayOfWeek[] days, TemporalAccessor time) {
                this.days = days;
                this.time = time;
            }
        }

        public final boolean schedulingEnabled;
        public final ZoneOffset scheduleTimezone;
        public final BackupScheduleEntry[] backupScheduleEntries;

        protected BackupScheduling(
            boolean schedulingEnabled, 
            ZoneOffset scheduleTimezone,
            BackupScheduleEntry[] backupScheduleEntries
            ) {

            this.schedulingEnabled = schedulingEnabled;
            this.scheduleTimezone = scheduleTimezone;
            this.backupScheduleEntries = backupScheduleEntries;
        }
    }

    public static class BackupList {
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

    public static class ExternalBackups {
        public static class ExternalBackupSource {
            public final String hostname;
            public final int port;
            public final String username;
            public final String password;
            public final String format;

            public ExternalBackupSource(String hostname, int port, String username, String password, String format) {
                this.hostname = hostname;
                this.port = port;
                this.username = username;
                this.password = password;
                this.format = format;
            }
        }

        public static class ExternalFTPSource extends ExternalBackupSource {
            public static class ExternalBackupListEntry {
                Path path;
                String[] blacklist;
            }

            public final Path baseDirectory;
            public final ExternalBackupListEntry backupListEntries;

            public ExternalFTPSource(
                String hostname, 
                int port, 
                String username, 
                String password, 
                String format,
                Path baseDirectory, 
                ExternalBackupListEntry backupListEntries) {
                super(hostname, port, username, password, format);

                this.baseDirectory = baseDirectory;
                this.backupListEntries = backupListEntries;
            }
        }

        public static class ExternalMySQLSource extends ExternalBackupSource {
            public static class MySQLDatabaseBackup {
                public final String name;
                public final String[] blacklist;

                public MySQLDatabaseBackup(String name, String[] blacklist) {
                    this.name = name;
                    this.blacklist = blacklist;
                }
            }

            public final boolean ssl;
            public final MySQLDatabaseBackup mySQLDatabaseBackups;

            public ExternalMySQLSource(
                String hostname, 
                int port, 
                String username, 
                String password, 
                String format,
                Path baseDirectory, 
                boolean ssl, 
                MySQLDatabaseBackup mySQLDatabaseBackup
                ) {
                super(hostname, port, username, password, format);

                this.ssl = ssl;
                this.mySQLDatabaseBackups = mySQLDatabaseBackup;
            }
        }

        public final ExternalBackupSource[] externalBackupSources;

        public ExternalBackups(
            ExternalBackupSource[] externalBackupSources
            ) {

            this.externalBackupSources = externalBackupSources;
        }
    }

    public static class BackupMethods {
        public static class BackupMethod {
            public final boolean enabled;

            public BackupMethod(boolean enabled) {
                this.enabled = enabled;
            }
        }

        public static class GoogleDriveBackupMethod extends BackupMethod {
            public GoogleDriveBackupMethod(boolean enabled) {
                super(enabled);
            }
        }

        public static class OneDriveBackupMethod extends BackupMethod {
            public OneDriveBackupMethod(boolean enabled) {
                super(enabled);
            }
        }

        public static class DropboxBackupMethod extends BackupMethod {
            public DropboxBackupMethod(boolean enabled) {
                super(enabled);
            }
        }

        public static class FTPBackupMethod extends BackupMethod {
            String hostname; 
            int port;
            boolean sftp;
            boolean ftps;
            String username;
            String password;
            Path publicKey;
            String passphrase;
            String baseDirectory;

            public FTPBackupMethod(
                boolean enabled, 
                String hostname, 
                int port, 
                boolean sftp, 
                boolean ftps, 
                String username, 
                String password, 
                Path publicKey, 
                String passphrase, 
                String baseDirectory
                ) {
                super(enabled);

                this.hostname = hostname;
                this.port = port;
                this.sftp = sftp;
                this.username = username;
                this.password = password;
                this.publicKey = publicKey;
                this.passphrase = passphrase;
                this.baseDirectory = baseDirectory;
            }
        }
    }

    public static class Advanced {
        public final boolean metrics;
        public final boolean updateCheck;
        public final boolean suppressErrors;
        public final String messagePrefix;
        public final String defaultMessageColor;
        public final Locale dateLanguage;

        public Advanced(boolean metrics,boolean updateCheck, boolean suppressErrors, String messagePrefix, String defaultMessageColor, Locale dateLanguage) {
            this.metrics = metrics;
            this.updateCheck = updateCheck;
            this.suppressErrors = suppressErrors;
            this.messagePrefix = messagePrefix;
            this.defaultMessageColor = defaultMessageColor;
            this.dateLanguage = dateLanguage;
        }
    }

    public final BackupStorage backupStorage;
    public final BackupScheduling backupScheduling;
    public final BackupList backupList;
    public final ExternalBackups externalBackups;
    public final BackupMethods backupMethods;

    public Config(
        BackupStorage backupStorage, 
        BackupScheduling backupScheduling, 
        BackupList backupList,
        ExternalBackups externalBackups,
        BackupMethods backupMethods
        ) {

        this.backupStorage = backupStorage;
        this.backupScheduling = backupScheduling;
        this.backupList = backupList;
        this.externalBackups = externalBackups;
        this.backupMethods = backupMethods;
    }
}

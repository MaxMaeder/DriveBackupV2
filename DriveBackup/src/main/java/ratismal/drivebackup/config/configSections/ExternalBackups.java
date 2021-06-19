package ratismal.drivebackup.config.configSections;

import java.nio.file.Path;

public class ExternalBackups {
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
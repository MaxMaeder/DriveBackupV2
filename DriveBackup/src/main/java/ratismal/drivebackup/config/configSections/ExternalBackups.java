package ratismal.drivebackup.config.configSections;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.ConfigParser.Logger;

public class ExternalBackups {
    public static class ExternalBackupSource {
        public final String hostname;
        public final int port;
        public final String username;
        public final String password;
        public final String format;

        private ExternalBackupSource(String hostname, int port, String username, String password, String format) {
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

        public final boolean sftp;
        public final boolean ftps;
        public final Path publicKey;
        public final String passphrase;
        public final Path baseDirectory;
        public final ExternalBackupListEntry backupListEntries;

        private ExternalFTPSource(
            String hostname, 
            int port, 
            String username, 
            String password, 
            String format,
            boolean sftp,
            boolean ftps,
            Path publicKey, 
            String passphrase, 
            Path baseDirectory, 
            ExternalBackupListEntry backupListEntries
            ) {
            super(hostname, port, username, password, format);

            this.sftp = sftp;
            this.ftps = ftps;
            this.publicKey = publicKey;
            this.passphrase = passphrase;
            this.baseDirectory = baseDirectory;
            this.backupListEntries = backupListEntries;
        }
    }

    public static class ExternalMySQLSource extends ExternalBackupSource {
        public static class MySQLDatabaseBackup {
            public final String name;
            public final String[] blacklist;

            private MySQLDatabaseBackup(String name, String[] blacklist) {
                this.name = name;
                this.blacklist = blacklist;
            }
        }

        public final boolean ssl;
        public final MySQLDatabaseBackup mySQLDatabaseBackups;

        private ExternalMySQLSource(
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

    private ExternalBackups(
        ExternalBackupSource[] externalBackupSources
        ) {

        this.externalBackupSources = externalBackupSources;
    }

    public static ExternalBackups parse(FileConfiguration config, Logger logger) {
        List<Map<?, ?>> rawList = config.getMapList("external-backup-list");
        ArrayList<ExternalBackupSource> list = new ArrayList<>();
        for (Map<?, ?> rawListEntry : rawList) {
            int entryIndex = rawList.indexOf(rawListEntry) + 1;

            String type;
            try {
                type = (String) rawListEntry.get("type");
            } catch (Exception e) {
                logger.log("Backup type invalid, skipping external backup entry " + entryIndex);
                continue;
            }

            switch (type) {
                case "ftpServer":
                case "ftpsServer":
                case "sftpServer":
                    try {
                        Path publicKey;
                        try {
                            publicKey = Path.of((String) rawListEntry.get("sftp-public-key"));

                        } catch (Exception e) {
                            logger.log("Path to public key invalid, skipping external backup entry " + entryIndex);
                            continue;
                        }

                        Path baseDirectory;
                        try {
                            baseDirectory = Path.of((String) rawListEntry.get("base-dir"));

                        } catch (Exception e) {
                            logger.log("Path to base directory invalid, skipping external backup entry " + entryIndex);
                            continue;
                        }



                        list.add(
                            new ExternalFTPSource(
                                (String) rawListEntry.get("hostname"), 
                                (int) rawListEntry.get("port"), 
                                (String) rawListEntry.get("username"), 
                                (String) rawListEntry.get("password"), 
                                rawListEntry.get("format"),
                                type == "sftpServer" ? true : false,
                                type == "ftpsServer" ? true : false,
                                publicKey,
                                (String) rawListEntry.get("passphrase"),
                                baseDirectory,
                                backupListEntries
                                )
                            );
                    } catch (Exception e) {
                        logger.log("External Backup entry " + entryIndex + " has an invalid configuration, skipping");
                        continue;
                    }

                    break;
                case "mysqlDatabase":
                    try {
                        
                    } catch (Exception e) {
                        logger.log("External Backup entry " + entryIndex + " has an invalid configuration, skipping");
                        continue;
                    }

                    break;
                default: 
                    logger.log("Backup type invalid, skipping external backup entry " + entryIndex);
                    continue;
            }
        }
    }
}
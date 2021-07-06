package ratismal.drivebackup.config.configSections;

import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Logger;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalFTPSource.ExternalBackupListEntry;
import ratismal.drivebackup.util.LocalDateTimeFormatter;

public class ExternalBackups {
    public static class ExternalBackupSource {
        public final String hostname;
        public final int port;
        public final String username;
        public final String password;
        public final LocalDateTimeFormatter format;

        private ExternalBackupSource(String hostname, int port, String username, String password, LocalDateTimeFormatter format) {
            this.hostname = hostname;
            this.port = port;
            this.username = username;
            this.password = password;
            this.format = format;
        }
    }

    public static class ExternalFTPSource extends ExternalBackupSource {
        public static class ExternalBackupListEntry {
            public final String path;
            public final String[] blacklist;

            private ExternalBackupListEntry(String path, String[] blacklist) {
                this.path = path;
                this.blacklist = blacklist;
            }
        }

        public final boolean sftp;
        public final boolean ftps;
        public final String publicKey;
        public final String passphrase;
        public final String baseDirectory;
        public final ExternalBackupListEntry[] backupList;

        private ExternalFTPSource(
            String hostname, 
            int port, 
            String username, 
            String password, 
            LocalDateTimeFormatter format,
            boolean sftp,
            boolean ftps,
            String publicKey, 
            String passphrase, 
            String baseDirectory, 
            ExternalBackupListEntry[] backupList
            ) {
            super(hostname, port, username, password, format);

            this.sftp = sftp;
            this.ftps = ftps;
            this.publicKey = publicKey;
            this.passphrase = passphrase;
            this.baseDirectory = baseDirectory;
            this.backupList = backupList;
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
        public final MySQLDatabaseBackup[] databaseList;

        private ExternalMySQLSource(
            String hostname, 
            int port, 
            String username, 
            String password, 
            LocalDateTimeFormatter format,
            boolean ssl, 
            MySQLDatabaseBackup[] databaseList
            ) {
            super(hostname, port, username, password, format);

            this.ssl = ssl;
            this.databaseList = databaseList;
        }
    }

    public final ExternalBackupSource[] sources;

    private ExternalBackups(
        ExternalBackupSource[] sources
        ) {

        this.sources = sources;
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

            String validTypes[] = {"ftpServer", "ftpsServer", "sftpServer", "mysqlDatabase"};
            if (!Arrays.asList(validTypes).contains(type)) {
                logger.log("Backup type invalid, skipping external backup entry " + entryIndex);
            }

            String hostname;
            int port;
            try {
                hostname = (String) rawListEntry.get("hostname");
                port = (int) (Integer) rawListEntry.get("port");
            } catch (Exception e) {
                logger.log("Hostname/port invalid, skipping external backup entry " + entryIndex);
                continue;
            }

            String username;
            String password;
            try {
                username = (String) rawListEntry.get("username");
                password = (String) rawListEntry.get("password");
            } catch (Exception e) {
                logger.log("Username/password invalid, skipping external backup entry " + entryIndex);
                continue;
            }

            LocalDateTimeFormatter formatter;
            try {
                formatter = LocalDateTimeFormatter.ofPattern((String) rawListEntry.get("format"));
            } catch (Exception e) {
                logger.log("Format invalid, skipping external backup entry " + entryIndex);
                if (e instanceof IllegalArgumentException) e.getMessage();
                continue;
            }

            switch (type) {
                case "ftpServer":
                case "ftpsServer":
                case "sftpServer":
                    String publicKey = "";
                    if (rawListEntry.containsKey("sftp-public-key")) {
                        try {
                            publicKey = ConfigParser.verifyPath((String) rawListEntry.get("sftp-public-key"));
                        } catch (Exception e) {
                            logger.log("Path to public key invalid in external backup entry " + entryIndex + ", leaving blank");
                            if (e instanceof InvalidPathException) e.getMessage();
                        }
                    }

                    String passphrase = "";
                    if (rawListEntry.containsKey("passphrase")) {
                        try {
                            passphrase = (String) rawListEntry.get("passphrase");
                        } catch (Exception e) {
                            logger.log("Passphrase invalid in external backup entry " + entryIndex + ", leaving blank");
                        }
                    }

                    String baseDirectory = "";
                    if (rawListEntry.containsKey("base-dir")) {
                        try {
                            baseDirectory = ConfigParser.verifyPath((String) rawListEntry.get("base-dir"));
                        } catch (Exception e) {
                            logger.log("Path to base directory key invalid in external backup entry " + entryIndex + ", leaving blank");
                            if (e instanceof InvalidPathException) e.getMessage();
                        }
                    }

                    List<Map<?, ?>> rawBackupList;
                    try {
                        rawBackupList = (List<Map<?, ?>>) rawListEntry.get("backup-list");
                    } catch (Exception e) {
                        logger.log("Backup list invalid, skipping external backup entry " + entryIndex);
                        continue;
                    }

                    ArrayList<ExternalBackupListEntry> backupList = new ArrayList<>();
                    for (Map<?, ?> rawBackupListEntry : rawBackupList) {
                        int entryBackupIndex = rawBackupList.indexOf(rawBackupListEntry) + 1;

                        String path;
                        try {
                            path = ConfigParser.verifyPath((String) rawBackupListEntry.get("path"));
                        } catch (Exception e) {
                            logger.log("Path invalid, skipping external backup backup list entry " + entryBackupIndex);
                            if (e instanceof InvalidPathException) e.getMessage();
                            continue;
                        }

                        String[] blacklist = new String[0];
                        if (rawBackupListEntry.containsKey("blacklist")) {
                            try {
                                blacklist = ((List<String>) rawBackupListEntry.get("blacklist")).toArray(new String[0]);
                            } catch (Exception e) {
                                logger.log("Blacklist invalid in external backup backup list entry " + entryBackupIndex + ", leaving blank");
                            }
                        }

                        backupList.add(
                            new ExternalBackupListEntry(path, blacklist)
                        );
                    }

                    list.add(
                        new ExternalFTPSource(
                            hostname, 
                            port, 
                            username, 
                            password, 
                            formatter,
                            type == "sftpServer",
                            type == "ftpsServer",
                            publicKey,
                            passphrase,
                            baseDirectory,
                            backupList.toArray(new ExternalBackupListEntry[0])
                            )
                        );

                    break;
                case "mysqlDatabase":
                    try {
                        
                    } catch (Exception e) {
                        logger.log("External Backup entry " + entryIndex + " has an invalid configuration, skipping");
                        continue;
                    }

                    break;
                default: 
                    // Should never get here
                    continue;
            }
        }

        return new ExternalBackups(
            list.toArray(new ExternalBackupSource[0])
            );
    }
}
package ratismal.drivebackup.config.configSections;

import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.FileConfiguration;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.util.Logger;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalFTPSource.ExternalBackupListEntry;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalMySQLSource.MySQLDatabaseBackup;
import ratismal.drivebackup.util.LocalDateTimeFormatter;

import static ratismal.drivebackup.config.Localization.intl;

public class ExternalBackups {
    public static class ExternalBackupSource {
        public final String hostname;
        public final int port;
        public final String username;
        public final String password;
        public final LocalDateTimeFormatter format;

        private ExternalBackupSource(String hostname, int port, String username, String password, LocalDateTimeFormatter formatter) {
            this.hostname = hostname;
            this.port = port;
            this.username = username;
            this.password = password;
            this.format = formatter;
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
            LocalDateTimeFormatter formatter,
            boolean sftp,
            boolean ftps,
            String publicKey, 
            String passphrase, 
            String baseDirectory, 
            ExternalBackupListEntry[] backupList
            ) {
            super(hostname, port, username, password, formatter);

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
            LocalDateTimeFormatter formatter,
            boolean ssl, 
            MySQLDatabaseBackup[] databaseList
            ) {
            super(hostname, port, username, password, formatter);

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

    @NotNull
    @Contract ("_, _ -> new")
    public static ExternalBackups parse(@NotNull FileConfiguration config, Logger logger) {
        List<Map<?, ?>> rawList = config.getMapList("external-backup-list");
        ArrayList<ExternalBackupSource> list = new ArrayList<>();
        for (Map<?, ?> rawListEntry : rawList) {
            String entryIndex = String.valueOf(rawList.indexOf(rawListEntry) + 1);
            String[] validTypes = {"ftpServer", "ftpsServer", "sftpServer", "mysqlDatabase"};
            String type;
            try {
                type = (String) rawListEntry.get("type");
                if (!Arrays.asList(validTypes).contains(type)) {
                    throw new Exception();
                }
            } catch (Exception e) {
                logger.log(intl("external-backup-type-invalid"), "entry", entryIndex);
                continue;
            }

            String hostname;
            int port;
            try {
                hostname = (String) rawListEntry.get("hostname");
                port = (Integer) rawListEntry.get("port");
            } catch (ClassCastException e) {
                logger.log(intl("external-backup-host-port-invalid"), "entry", entryIndex);
                continue;
            }

            // TODO: password optional, should be treated as such
            String username;
            String password;
            try {
                username = (String) rawListEntry.get("username");
                password = (String) rawListEntry.get("password");
            } catch (ClassCastException e) {
                logger.log(intl("external-backup-user-pass-invalid"), "entry", entryIndex);
                continue;
            }
            LocalDateTimeFormatter formatter;
            try {
                formatter = LocalDateTimeFormatter.ofPattern((String) rawListEntry.get("format"));
            } catch (IllegalArgumentException | ClassCastException e) {
                logger.log(intl("external-backup-format-invalid"), "entry", entryIndex);
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
                        } catch (IllegalArgumentException | ClassCastException e) {
                            logger.log(intl("external-backup-public-key-invalid"), "entry", entryIndex);
                        }
                    }
                    String passphrase = "";
                    if (rawListEntry.containsKey("passphrase")) {
                        try {
                            passphrase = (String) rawListEntry.get("passphrase");
                        } catch (ClassCastException e) {
                            logger.log(intl("external-backup-passphrase-invalid"), "entry", entryIndex);
                        }
                    }
                    String baseDirectory = "";
                    if (rawListEntry.containsKey("base-dir")) {
                        try {
                            baseDirectory = ConfigParser.verifyPath((String) rawListEntry.get("base-dir"));
                        } catch (IllegalArgumentException | ClassCastException e) {
                            logger.log(intl("external-backup-base-dir-invalid"), "entry", entryIndex);
                        }
                    }
                    List<Map<?, ?>> rawBackupList;
                    try {
                        rawBackupList = (List<Map<?, ?>>) rawListEntry.get("backup-list");
                    } catch (ClassCastException e) {
                        logger.log(intl("external-backup-list-invalid"), "entry", entryIndex);
                        continue;
                    }
                    List<ExternalBackupListEntry> backupList = new ArrayList<>();
                    for (Map<?, ?> rawBackupListEntry : rawBackupList) {
                        String entryBackupIndex = String.valueOf(rawBackupList.indexOf(rawBackupListEntry) + 1);
                        String path;
                        try {
                            path = ConfigParser.verifyPath((String) rawBackupListEntry.get("path"));
                        } catch (IllegalArgumentException | ClassCastException e) {
                            logger.log(intl("external-backup-list-path-invalid"), "entry-backup", entryBackupIndex);
                            continue;
                        }
                        String[] blacklist = new String[0];
                        if (rawBackupListEntry.containsKey("blacklist")) {
                            try {
                                blacklist = ((List<String>) rawBackupListEntry.get("blacklist")).toArray(new String[0]);
                            } catch (ArrayStoreException | ClassCastException e) {
                                logger.log(intl("external-backup-list-blacklist-invalid"), "entry-backup", entryBackupIndex);
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
                            type.equals("sftpServer"),
                            type.equals("ftpsServer"),
                            publicKey,
                            passphrase,
                            baseDirectory,
                            backupList.toArray(new ExternalBackupListEntry[0])
                            )
                        );
                    break;
                case "mysqlDatabase":
                    boolean ssl = false;
                    try {
                        ssl = (Boolean) rawListEntry.get("ssl");
                    } catch (ClassCastException e) {
                        logger.log(intl("external-database-ssl-invalid"), "entry", entryIndex);
                        // Use false
                    }
                    List<Map<?, ?>> rawDatabaseList;
                    try {
                        rawDatabaseList = (List<Map<?, ?>>) rawListEntry.get("databases");
                    } catch (ClassCastException e) {
                        logger.log(intl("external-database-list-invalid"), "entry", entryIndex);
                        continue;
                    }
                    List<MySQLDatabaseBackup> databaseList = new ArrayList<>();
                    for (Map<?, ?> rawDatabaseListEntry : rawDatabaseList) { 
                        String entryDatabaseIndex = String.valueOf(rawDatabaseList.indexOf(rawDatabaseListEntry) + 1);
                        String name;
                        try {
                            name = (String) rawDatabaseListEntry.get("name");
                        } catch (ClassCastException e) {
                            logger.log(intl("external-database-list-name-invalid"), "entry-database", entryDatabaseIndex);
                            continue;
                        }
                        String[] blacklist = new String[0];
                        if (rawDatabaseListEntry.containsKey("blacklist")) {
                            try {
                                blacklist = ((List<String>) rawDatabaseListEntry.get("blacklist")).toArray(new String[0]);
                            } catch (ArrayStoreException | ClassCastException e) {
                                logger.log(intl("external-database-list-blacklist-invalid"), "entry-database", entryDatabaseIndex);
                            }
                        }
                        databaseList.add(new MySQLDatabaseBackup(name, blacklist));
                    }
                    list.add(
                        new ExternalMySQLSource(
                            hostname, 
                            port, 
                            username, 
                            password, 
                            formatter, 
                            ssl, 
                            databaseList.toArray(new MySQLDatabaseBackup[0]))
                    );
                    break;
                default: 
                    // Should never get here
            }
        }
        return new ExternalBackups(
            list.toArray(new ExternalBackupSource[0])
            );
    }
}

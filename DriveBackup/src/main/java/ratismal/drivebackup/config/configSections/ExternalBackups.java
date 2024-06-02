package ratismal.drivebackup.config.configSections;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalFTPSource.ExternalBackupListEntry;
import ratismal.drivebackup.config.configSections.ExternalBackups.ExternalMySQLSource.MySQLDatabaseBackup;
import ratismal.drivebackup.util.LocalDateTimeFormatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Deprecated
public class ExternalBackups {
    public static class ExternalBackupSource {
        public final String hostname;
        public final int port;
        public final String username;
        public final String password;
        public final LocalDateTimeFormatter format;

        @Contract (pure = true)
        private ExternalBackupSource(String hostname, int port, String username, String password, LocalDateTimeFormatter formatter) {
            this.hostname = hostname;
            this.port = port;
            this.username = username;
            this.password = password;
            format = formatter;
        }
    }

    @Deprecated
    public static class ExternalFTPSource extends ExternalBackupSource {
        @Deprecated
        public static class ExternalBackupListEntry {
            public final String path;
            public final String[] blacklist;

            @Contract (pure = true)
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

    @Deprecated
    public static class ExternalMySQLSource extends ExternalBackupSource {
        @Deprecated
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

    @Contract (pure = true)
    private ExternalBackups(
        ExternalBackupSource[] sources
        ) {

        this.sources = sources;
    }

    @NotNull
    @Contract ("_ -> new")
    public static ExternalBackups parse(@NotNull FileConfiguration config) {
        List<Map<?, ?>> rawList = config.getMapList("external-backup-list");
        ArrayList<ExternalBackupSource> list = new ArrayList<>();
        for (Map<?, ?> rawListEntry : rawList) {
            String entryIndex = String.valueOf(rawList.indexOf(rawListEntry) + 1);
            String[] validTypes = {"ftpServer", "ftpsServer", "sftpServer", "mysqlDatabase"};
            String type;
            try {
                type = (String) rawListEntry.get("type");
                if (!Arrays.asList(validTypes).contains(type)) {
                    throw new IllegalArgumentException("Unsupported type");
                }
            } catch (Exception e) {
                continue;
            }

            String hostname;
            int port;
            try {
                hostname = (String) rawListEntry.get("hostname");
                port = (Integer) rawListEntry.get("port");
            } catch (ClassCastException e) {
                continue;
            }

            // TODO: password optional, should be treated as such
            String username;
            String password;
            try {
                username = (String) rawListEntry.get("username");
                password = (String) rawListEntry.get("password");
            } catch (ClassCastException e) {
                continue;
            }
            LocalDateTimeFormatter formatter;
            try {
                formatter = LocalDateTimeFormatter.ofPattern(null, (String) rawListEntry.get("format"));
            } catch (IllegalArgumentException | ClassCastException e) {
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
                        } catch (IllegalArgumentException | ClassCastException ignored) {
                        }
                    }
                    String passphrase = "";
                    if (rawListEntry.containsKey("passphrase")) {
                        try {
                            passphrase = (String) rawListEntry.get("passphrase");
                        } catch (ClassCastException ignored) {
                        }
                    }
                    String baseDirectory = "";
                    if (rawListEntry.containsKey("base-dir")) {
                        try {
                            baseDirectory = ConfigParser.verifyPath((String) rawListEntry.get("base-dir"));
                        } catch (IllegalArgumentException | ClassCastException ignored) {
                        }
                    }
                    List<Map<?, ?>> rawBackupList;
                    try {
                        rawBackupList = (List<Map<?, ?>>) rawListEntry.get("backup-list");
                    } catch (ClassCastException e) {
                        continue;
                    }
                    List<ExternalBackupListEntry> backupList = new ArrayList<>();
                    for (Map<?, ?> rawBackupListEntry : rawBackupList) {
                        String entryBackupIndex = String.valueOf(rawBackupList.indexOf(rawBackupListEntry) + 1);
                        String path;
                        try {
                            path = ConfigParser.verifyPath((String) rawBackupListEntry.get("path"));
                        } catch (IllegalArgumentException | ClassCastException e) {
                            continue;
                        }
                        String[] blacklist = new String[0];
                        if (rawBackupListEntry.containsKey("blacklist")) {
                            try {
                                blacklist = ((List<String>) rawBackupListEntry.get("blacklist")).toArray(new String[0]);
                            } catch (ArrayStoreException | ClassCastException ignored) {
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
                        // Use false
                    }
                    List<Map<?, ?>> rawDatabaseList;
                    try {
                        rawDatabaseList = (List<Map<?, ?>>) rawListEntry.get("databases");
                    } catch (ClassCastException e) {
                        continue;
                    }
                    List<MySQLDatabaseBackup> databaseList = new ArrayList<>();
                    for (Map<?, ?> rawDatabaseListEntry : rawDatabaseList) { 
                        String entryDatabaseIndex = String.valueOf(rawDatabaseList.indexOf(rawDatabaseListEntry) + 1);
                        String name;
                        try {
                            name = (String) rawDatabaseListEntry.get("name");
                        } catch (ClassCastException e) {
                            continue;
                        }
                        String[] blacklist = new String[0];
                        if (rawDatabaseListEntry.containsKey("blacklist")) {
                            try {
                                blacklist = ((List<String>) rawDatabaseListEntry.get("blacklist")).toArray(new String[0]);
                            } catch (ArrayStoreException | ClassCastException ignored) {
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

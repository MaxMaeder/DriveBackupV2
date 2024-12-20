package ratismal.drivebackupOld.config.configSections;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.objects.ExternalBackupListEntry;
import ratismal.drivebackup.objects.ExternalBackupSource;
import ratismal.drivebackup.objects.ExternalDatabaseEntry;
import ratismal.drivebackup.objects.ExternalFTPSource;
import ratismal.drivebackup.objects.ExternalMySQLSource;
import ratismal.drivebackup.util.LocalDateTimeFormatter;
import ratismal.drivebackupOld.config.ConfigParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Deprecated
public class ExternalBackups {
    
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
                    List<ExternalDatabaseEntry> databaseList = new ArrayList<>();
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
                        databaseList.add(new ExternalDatabaseEntry(name, blacklist));
                    }
                    list.add(
                        new ExternalMySQLSource(
                            hostname, 
                            port, 
                            username, 
                            password, 
                            formatter, 
                            ssl, 
                            databaseList.toArray(new ExternalDatabaseEntry[0]))
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

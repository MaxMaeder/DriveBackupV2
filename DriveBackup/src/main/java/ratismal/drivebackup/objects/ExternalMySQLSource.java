package ratismal.drivebackup.objects;

import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.util.LocalDateTimeFormatter;

public final class ExternalMySQLSource extends ExternalBackupSource {
    
    public final boolean ssl;
    public final ExternalDatabaseEntry[] databaseList;
    
    public ExternalMySQLSource(
            String hostname,
            int port,
            String username,
            String password,
            LocalDateTimeFormatter formatter,
            boolean ssl,
            ExternalDatabaseEntry[] databaseList) {
        super(hostname, port, username, password, formatter);
        this.ssl = ssl;
        this.databaseList = databaseList;
    }
    
    @NotNull
    public String getTempFolderName() {
        return "mysql-" + getSocketAddress();
    }
    
}

package ratismal.drivebackup.objects;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.util.LocalDateTimeFormatter;

public abstract class ExternalBackupSource {
    
    public final String hostname;
    public final int port;
    public final String username;
    public final String password;
    public final LocalDateTimeFormatter format;
    
    @Contract (pure = true)
    protected ExternalBackupSource(
            String hostname,
            int port,
            String username,
            String password,
            LocalDateTimeFormatter formatter) {
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.password = password;
        format = formatter;
    }
    
    /**
     * Gets the socket address (ipaddress/hostname:port) of an external backup server based on the specified settings.
     * @return the socket address
     */
    @NotNull
    @Contract (pure = true)
    public String getSocketAddress() {
        return hostname + ":" + port;
    }
    
    /**
     * Generates the name for a folder based on the specified external backup settings to be stored within the external-backups temporary folder.
     * @return the folder name
     */
    @NotNull
    public abstract String getTempFolderName();
    
}

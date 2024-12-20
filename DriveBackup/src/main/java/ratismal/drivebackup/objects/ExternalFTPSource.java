package ratismal.drivebackup.objects;

import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.util.LocalDateTimeFormatter;

public final class ExternalFTPSource extends ExternalBackupSource {
    
    public final boolean sftp;
    public final boolean ftps;
    public final String publicKey;
    public final String passphrase;
    public final String baseDirectory;
    public final ExternalBackupListEntry[] backupList;
    
    public ExternalFTPSource(
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
            ExternalBackupListEntry[] backupList) {
        super(hostname, port, username, password, formatter);
        this.sftp = sftp;
        this.ftps = ftps;
        this.publicKey = publicKey;
        this.passphrase = passphrase;
        this.baseDirectory = baseDirectory;
        this.backupList = backupList;
    }
    
    @NotNull
    public String getTempFolderName() {
        return "ftp-" + getSocketAddress();
    }
    
}

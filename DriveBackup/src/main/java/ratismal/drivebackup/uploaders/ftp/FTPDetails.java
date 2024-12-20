package ratismal.drivebackup.uploaders.ftp;

import lombok.AccessLevel;
import lombok.Getter;
import ratismal.drivebackup.configuration.ConfigurationObject;
import ratismal.drivebackup.configuration.ConfigurationSection;
import ratismal.drivebackup.platforms.DriveBackupInstance;

@Getter(AccessLevel.PACKAGE)
public final class FTPDetails {
    
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String directory;
    private final boolean sftp;
    private final boolean ftps;
    private final String publicKey;
    private final String passphrase;
    
    public FTPDetails(
            String host,
            int port,
            String username,
            String password,
            String directory,
            boolean sftp,
            boolean ftps,
            String publicKey,
            String passphrase) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.directory = directory;
        this.sftp = sftp;
        this.ftps = ftps;
        this.publicKey = publicKey;
        this.passphrase = passphrase;
    }
    
    public static FTPDetails load(DriveBackupInstance instance) {
        ConfigurationObject config = instance.getConfigHandler().getConfig();
        ConfigurationSection ftpSection = new ConfigurationSection(config, "ftp");
        String hostname = ftpSection.getValue("hostname").getString();
        int port = ftpSection.getValue("port").getInt();
        String username = ftpSection.getValue("username").getString();
        String password = ftpSection.getValue("password").getString();
        String directory = ftpSection.getValue("working-dir").getString();
        boolean sftp = ftpSection.getValue("sftp").getBoolean();
        boolean ftps = ftpSection.getValue("ftps").getBoolean();
        String publicKey = ftpSection.getValue("public-key").getString();
        String passphrase = ftpSection.getValue("passphrase").getString();
        
        return new FTPDetails(hostname, port, username, password, directory, sftp, ftps, publicKey, passphrase);
    }
    
}

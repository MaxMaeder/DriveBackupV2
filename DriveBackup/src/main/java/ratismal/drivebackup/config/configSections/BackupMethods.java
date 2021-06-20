package ratismal.drivebackup.config.configSections;

import java.nio.file.Path;

import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.ConfigParser.Logger;

public class BackupMethods {
    public static class BackupMethod {
        public final boolean enabled;

        public BackupMethod(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class GoogleDriveBackupMethod extends BackupMethod {
        public GoogleDriveBackupMethod(boolean enabled) {
            super(enabled);
        }
    }

    public static class OneDriveBackupMethod extends BackupMethod {
        public OneDriveBackupMethod(boolean enabled) {
            super(enabled);
        }
    }

    public static class DropboxBackupMethod extends BackupMethod {
        public DropboxBackupMethod(boolean enabled) {
            super(enabled);
        }
    }

    public static class FTPBackupMethod extends BackupMethod {
        String hostname; 
        int port;
        boolean sftp;
        boolean ftps;
        String username;
        String password;
        Path publicKey;
        String passphrase;
        Path baseDirectory;

        public FTPBackupMethod(
            boolean enabled, 
            String hostname, 
            int port, 
            boolean sftp, 
            boolean ftps, 
            String username, 
            String password, 
            Path publicKey, 
            String passphrase, 
            Path baseDirectory
            ) {
            super(enabled);

            this.hostname = hostname;
            this.port = port;
            this.sftp = sftp;
            this.username = username;
            this.password = password;
            this.publicKey = publicKey;
            this.passphrase = passphrase;
            this.baseDirectory = baseDirectory;
        }
    }

    public final GoogleDriveBackupMethod googleDriveMethod;
    public final OneDriveBackupMethod oneDriveMethod;
    public final DropboxBackupMethod dropboxMethod;
    public final FTPBackupMethod ftpMethod;

    private BackupMethods(GoogleDriveBackupMethod googleDriveMethod, OneDriveBackupMethod oneDriveMethod, DropboxBackupMethod dropboxMethod, FTPBackupMethod ftpMethod) {
        this.googleDriveMethod = googleDriveMethod;
        this.oneDriveMethod = oneDriveMethod;
        this.dropboxMethod = dropboxMethod;
        this.ftpMethod = ftpMethod;
    }

    public static BackupMethods parse(FileConfiguration config, Logger logger) {
        GoogleDriveBackupMethod googleDriveMethod = new GoogleDriveBackupMethod(
            config.getBoolean("googledrive.enabled")
            );

        OneDriveBackupMethod oneDriveMethod = new OneDriveBackupMethod(
            config.getBoolean("onedrive.enabled")
            );

        DropboxBackupMethod dropboxMethod = new DropboxBackupMethod(
            config.getBoolean("dropbox.enabled")
            );

        boolean ftpEnabled = config.getBoolean("ftp.enabled");

        Path publicKey = Path.of("");
        try {
            publicKey = Path.of(config.getString("ftp.sftp-public-key"));
        } catch (Exception e) {
            logger.log("Path to public key invalid, disabling the FTP backup method");
            ftpEnabled = false;
        }

        Path baseDir = Path.of("");
        try {
            baseDir = Path.of(config.getString("ftp.base-dir"));
        } catch (Exception e) {
            logger.log("Path to base dir invalid, disabling the FTP backup method");
            ftpEnabled = false;
        }

        FTPBackupMethod ftpMethod = new FTPBackupMethod(
            ftpEnabled, 
            config.getString("ftp.hostname"), 
            config.getInt("ftp.port"), 
            config.getBoolean("ftp.sftp"), 
            config.getBoolean("ftp.ftps"), 
            config.getString("ftp.username"), 
            config.getString("ftp.password"), 
            publicKey, 
            config.getString("ftp.sftp-passphrase"), 
            baseDir
            );

        return new BackupMethods(googleDriveMethod, oneDriveMethod, dropboxMethod, ftpMethod);
    }
}
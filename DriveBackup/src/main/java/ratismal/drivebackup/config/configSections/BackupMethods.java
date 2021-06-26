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
        public final String hostname; 
        public final int port;
        public final boolean sftp;
        public final boolean ftps;
        public final String username;
        public final String password;
        public final Path publicKey;
        public final String passphrase;
        public final Path baseDirectory;

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
            this.ftps = ftps;
            this.username = username;
            this.password = password;
            this.publicKey = publicKey;
            this.passphrase = passphrase;
            this.baseDirectory = baseDirectory;
        }
    }

    public final GoogleDriveBackupMethod googleDrive;
    public final OneDriveBackupMethod oneDrive;
    public final DropboxBackupMethod dropbox;
    public final FTPBackupMethod ftp;

    private BackupMethods(GoogleDriveBackupMethod googleDrive, OneDriveBackupMethod oneDrive, DropboxBackupMethod dropbox, FTPBackupMethod ftp) {
        this.googleDrive = googleDrive;
        this.oneDrive = oneDrive;
        this.dropbox = dropbox;
        this.ftp = ftp;
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
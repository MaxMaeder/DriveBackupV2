package ratismal.drivebackup.config.configSections;

import com.google.api.client.util.Strings;

import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.ConfigParser;
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
        public final String publicKey;
        public final String passphrase;
        public final String baseDirectory;

        public FTPBackupMethod(
            boolean enabled, 
            String hostname, 
            int port, 
            boolean sftp, 
            boolean ftps, 
            String username, 
            String password, 
            String publicKey, 
            String passphrase, 
            String baseDirectory
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

    public BackupMethods(GoogleDriveBackupMethod googleDrive, OneDriveBackupMethod oneDrive, DropboxBackupMethod dropbox, FTPBackupMethod ftp) {
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

        String publicKey = "";
        if (Strings.isNullOrEmpty(config.getString("ftp.sftp-public-key")) && ftpEnabled) {
            try {
                publicKey = ConfigParser.verifyPath(config.getString("ftp.sftp-public-key").trim());
            } catch (Exception e) {
                // TODO:: ERROR
            }
        }

        String baseDir = "";
        if (Strings.isNullOrEmpty(config.getString("ftp.base-dir")) && ftpEnabled) {
            try {
                baseDir = ConfigParser.verifyPath(config.getString("ftp.base-dir").trim());
            } catch (Exception e) {
                // TODO:: ERROR
            }
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
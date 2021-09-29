package ratismal.drivebackup.config.configSections;

import com.google.api.client.util.Strings;

import org.bukkit.configuration.file.FileConfiguration;

import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.util.Logger;

import static ratismal.drivebackup.config.Localization.intl;

public class BackupMethods {
    public static class BackupMethod {
        public final boolean enabled;

        public BackupMethod(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class GoogleDriveBackupMethod extends BackupMethod {
        public final String sharedDriveId;

        public GoogleDriveBackupMethod(boolean enabled, String sharedDriveId) {
            super(enabled);
            this.sharedDriveId = sharedDriveId;
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

    public static class WebDAVBackupMethod extends BackupMethod {
        public final String hostname;
        public final String username;
        public final String password;

        public WebDAVBackupMethod(
            boolean enabled,
            String hostname,
            String username,
            String password
            ) {
            super(enabled);

            this.hostname = hostname;
            this.username = username;
            this.password = password;
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
    public final WebDAVBackupMethod webdav;
    public final FTPBackupMethod ftp;

    public BackupMethods(GoogleDriveBackupMethod googleDrive, OneDriveBackupMethod oneDrive, DropboxBackupMethod dropbox, WebDAVBackupMethod webdav, FTPBackupMethod ftp) {
        this.googleDrive = googleDrive;
        this.oneDrive = oneDrive;
        this.dropbox = dropbox;
        this.webdav = webdav;
        this.ftp = ftp;
    }

    public static BackupMethods parse(FileConfiguration config, Logger logger) {
        GoogleDriveBackupMethod googleDriveMethod = new GoogleDriveBackupMethod(
            config.getBoolean("googledrive.enabled"),
            config.getString("googledrive.shared-drive-id").trim()
            );

        OneDriveBackupMethod oneDriveMethod = new OneDriveBackupMethod(
            config.getBoolean("onedrive.enabled")
            );

        DropboxBackupMethod dropboxMethod = new DropboxBackupMethod(
            config.getBoolean("dropbox.enabled")
            );

        WebDAVBackupMethod webdavMethod = new WebDAVBackupMethod(
            config.getBoolean("webdav.enabled"), 
            config.getString("webdav.hostname"),
            config.getString("webdav.username"), 
            config.getString("webdav.password")
            );

        boolean ftpEnabled = config.getBoolean("ftp.enabled");

        String publicKey = "";
        if (!Strings.isNullOrEmpty(config.getString("ftp.sftp-public-key")) && ftpEnabled) {
            try {
                publicKey = ConfigParser.verifyPath(config.getString("ftp.sftp-public-key"));
            } catch (Exception e) {
                logger.log(intl("ftp-method-pubic-key-invalid"));
            }
        }

        String baseDir = "";
        if (!Strings.isNullOrEmpty(config.getString("ftp.base-dir")) && ftpEnabled) {
            try {
                baseDir = ConfigParser.verifyPath(config.getString("ftp.base-dir"));
            } catch (Exception e) {
                logger.log(intl("ftp-method-passphrase-invalid"));
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

        return new BackupMethods(googleDriveMethod, oneDriveMethod, dropboxMethod, webdavMethod, ftpMethod);
    }
}
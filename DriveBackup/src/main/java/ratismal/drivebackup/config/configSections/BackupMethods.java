package ratismal.drivebackup.config.configSections;

import java.nio.file.Path;

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
        String baseDirectory;

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
            String baseDirectory
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
}
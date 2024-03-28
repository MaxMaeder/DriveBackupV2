package ratismal.drivebackup.constants;

import org.jetbrains.annotations.Contract;

public enum BackupMethod {
    GOOGLE_DRIVE("Google Drive"),
    ONE_DRIVE("OneDrive"),
    DROPBOX("Dropbox"),
    WEBDAV("WebDAV"),
    NEXTCLOUD("Nextcloud"),
    FTP("FTP"),
    SFTP("SFTP");
    
    private final String name;
    
    @Contract (pure = true)
    BackupMethod(String name) {
        this.name = name;
    }
    
    @Contract (pure = true)
    public String getName() {
        return name;
    }
    
    @Contract (pure = true)
    @Override
    public String toString() {
        return name;
    }
}

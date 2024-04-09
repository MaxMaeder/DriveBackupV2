package ratismal.drivebackup.uploaders;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.plugin.DriveBackup;

public enum AuthenticationProvider {
    GOOGLE_DRIVE("Google Drive", "googledrive", "/GoogleDriveCredential.json", "qWd2xXC/ORzdZvUotXoWhHC0POkMNuO/xuwcKWc9s1LLodayZXvkdKimmpOQqWYS6I+qGSrYNb8UCJWMhrgDXhIWEbDvytkQTwq+uNcnfw8=", "pasQz0KvtyC7o6CrlLPSMVV9Y0RMX76cXzsAbBoCBxI="),
    ONEDRIVE("OneDrive", "onedrive", "/OneDriveCredential.json", "Ktj7Jd1h0oYNVicuyTBk5fU+gHS+QYReZxZKNZNO9CDxxHaf8bXlw0SKO9jnwc81", ""),
    DROPBOX("Dropbox", "dropbox", "/DropboxCredential.json", "OSpqXymVUFSRnANAmj2DTA==", "4MrYNbN0I6J/fsAFeF00GQ==");
    
    private final String name;
    private final String id;
    private final String credStoreLocation;
    private final String clientId;
    private final String clientSecret;
    
    @Contract (pure = true)
    AuthenticationProvider(String name, String id, String credStoreLocation, String clientId, String clientSecret) {
        this.name = name;
        this.id = id;
        this.credStoreLocation = credStoreLocation;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }
    
    @Contract (pure = true)
    public String getName() {
        return name;
    }
    
    @Contract (pure = true)
    public String getId() {
        return id;
    }
    
    public @NotNull String getCredStoreLocation() {
        return DriveBackup.getInstance().getDataFolder().getAbsolutePath() + credStoreLocation;
    }
    
    @Contract (pure = true)
    public String getClientId() {
        return clientId;
    }
    
    @Contract (pure = true)
    public String getClientSecret() {
        return clientSecret;
    }
}

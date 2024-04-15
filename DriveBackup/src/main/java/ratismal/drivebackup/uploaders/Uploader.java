package ratismal.drivebackup.uploaders;

import org.jetbrains.annotations.Contract;
import ratismal.drivebackup.UploadThread;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.io.File;
import java.io.IOException;

public abstract class Uploader {
    
    protected final DriveBackupInstance instance;
    protected String name;
    protected String id;
    protected boolean authenticated;
    protected boolean errorOccurred;
    protected final AuthenticationProvider authProvider;
    protected final UploadThread.UploadLogger logger;
    
    @Contract (pure = true)
    protected Uploader(DriveBackupInstance instance, String name, String id, AuthenticationProvider authProvider, UploadThread.UploadLogger logger) {
        this.instance = instance;
        this.name = name;
        this.id = id;
        this.authProvider = authProvider;
        if (authProvider == null) {
            authenticated = true;
        }
        this.logger = logger;
    }
    
    public String getName() {
        return name;
    }
    
    protected void setName(String name) {
        this.name = name;
    }
    
    public String getId() {
        return id;
    }
    
    protected void setId(String id) {
        this.id = id;
    }
    
    public AuthenticationProvider getAuthProvider() {
        return authProvider;
    }
    
    public boolean isAuthenticated() {
        return authenticated;
    }
    
    protected void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
    
    public boolean didErrorOccur() {
        return errorOccurred;
    }
    
    protected void setErrorOccurred(boolean errorOccurred) {
        this.errorOccurred = errorOccurred;
    }
    
    public abstract void test(File testFile);
    
    public abstract void uploadFile(File file, String type) throws IOException;
    
    public abstract void close();
    
}

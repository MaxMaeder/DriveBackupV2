package ratismal.drivebackup.uploaders;

import org.jetbrains.annotations.Contract;
import ratismal.drivebackup.UploadThread;

import java.io.IOException;

public abstract class Uploader {
    private String name;
    private String id;
    private boolean authenticated;
    private boolean errorOccurred;
    private AuthenticationProvider authProvider;
    protected UploadThread.UploadLogger logger;
    
    @Contract (pure = true)
    protected Uploader(String name, String id) {
        this.name = name;
        this.id = id;
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
    protected void setAuthProvider(AuthenticationProvider authProvider) {
        this.authProvider = authProvider;
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
    public abstract void test(java.io.File testFile);
    public abstract void uploadFile(java.io.File file, String type) throws IOException;
    public abstract void close();
    
}

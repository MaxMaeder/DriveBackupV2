package ratismal.drivebackup.uploaders;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Contract;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.io.File;
import java.io.IOException;

@Accessors(makeFinal = true)
public abstract class Uploader {
    
    protected final DriveBackupInstance instance;
    @Getter
    @Setter
    private String name;
    @Getter
    @Setter
    private String id;
    @Getter
    @Setter
    @Accessors(makeFinal = false)
    private boolean authenticated;
    @Setter
    private boolean errorOccurred;
    @Getter
    protected final AuthenticationProvider authProvider;
    protected final UploadLogger logger;
    
    @Contract (pure = true)
    protected Uploader(DriveBackupInstance instance, String name, String id, AuthenticationProvider authProvider, UploadLogger logger) {
        this.instance = instance;
        this.name = name;
        this.id = id;
        this.authProvider = authProvider;
        if (authProvider == null) {
            authenticated = true;
        }
        this.logger = logger;
    }
    
    public boolean didErrorOccur() {
        return errorOccurred;
    }
    
    public abstract void test(File testFile);
    
    public abstract void uploadFile(File file, String type) throws IOException;
    
    public abstract void close();
    
    protected String getLocalSaveDirectory() {
        return instance.getConfigHandler().getConfig().getValue("local-save-directory").getString();
    }
    
    public String getRemoteSaveDirectory() {
        return instance.getConfigHandler().getConfig().getValue("remote-save-directory").getString();
    }
    
    protected int getKeepCount() {
        return instance.getConfigHandler().getConfig().getValue("keep-count").getInt();
    }
    
}

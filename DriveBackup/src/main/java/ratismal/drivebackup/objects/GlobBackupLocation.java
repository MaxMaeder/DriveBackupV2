package ratismal.drivebackup.objects;

import ratismal.drivebackup.util.FileUtil;

import java.nio.file.Path;
import java.util.List;

public class GlobBackupLocation implements BackupLocation {
    
    private final String glob;
    
    public GlobBackupLocation(String glob) {
        this.glob = glob;
    }
    
    public List<Path> getPaths() {
        return FileUtil.generateGlobFolderList(glob, ".");
    }
    
    public String toString() {
        return glob;
    }
    
}

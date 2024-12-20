package ratismal.drivebackup.objects;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class PathBackupLocation implements BackupLocation {
    
    private final Path path;
    
    public PathBackupLocation(String path) {
        this.path = Paths.get(path);
    }
    
    public List<Path> getPaths() {
        return Collections.singletonList(path);
    }
    
    public String toString() {
        return path.toString();
    }
    
}

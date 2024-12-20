package ratismal.drivebackup.objects;

import java.nio.file.Path;
import java.util.List;

public interface BackupLocation {
    
    List<Path> getPaths();
    
    String toString();
    
}

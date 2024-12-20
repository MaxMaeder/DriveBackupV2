package ratismal.drivebackup.uploaders;

import lombok.Getter;
import okhttp3.MediaType;
import org.jetbrains.annotations.Contract;

public final class UploaderUtils {
    
    @Getter
    private static final MediaType zipMediaType = MediaType.parse("application/zip; charset=utf-8");
    @Getter
    private static final MediaType jsonMediaType = MediaType.parse("application/json; charset=utf-8");
    @Getter
    private static final MediaType octetStreamMediaType = MediaType.parse("application/octet-stream");
    
    @Contract (value = " -> fail", pure = true)
    private UploaderUtils() {
        throw new IllegalStateException("Utility class");
    }
    
}

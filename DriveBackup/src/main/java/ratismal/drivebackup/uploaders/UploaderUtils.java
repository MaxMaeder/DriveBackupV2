package ratismal.drivebackup.uploaders;

import okhttp3.MediaType;
import org.jetbrains.annotations.Contract;

public final class UploaderUtils {
    
    private static final MediaType zipMediaType = MediaType.parse("application/zip; charset=utf-8");
    private static final MediaType jsonMediaType = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType octetStreamMediaType = MediaType.parse("application/octet-stream");
    
    @Contract (value = " -> fail", pure = true)
    private UploaderUtils() {
        throw new IllegalStateException("Utility class");
    }
    
    @Contract (pure = true)
    public static MediaType getZipMediaType() {
        return zipMediaType;
    }
    
    @Contract (pure = true)
    public static MediaType getJsonMediaType() {
        return jsonMediaType;
    }
    
    @Contract (pure = true)
    public static MediaType getOctetStreamMediaType() {
        return octetStreamMediaType;
    }
    
}

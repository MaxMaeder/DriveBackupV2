package ratismal.drivebackup.util;

import org.jetbrains.annotations.Contract;
import ratismal.drivebackup.uploaders.UploadLogger;

import java.net.UnknownHostException;

@Deprecated
public final class NetUtil {
    
    @Contract (pure = true)
    private NetUtil() {}

    public static void catchException(Exception exception, String domain, UploadLogger logger) {
        if (!(exception instanceof UnknownHostException)) {
            return;
        }
        logger.log("connection-error", "domain", domain);
    }
}

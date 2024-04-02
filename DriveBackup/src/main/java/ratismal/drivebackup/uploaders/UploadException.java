package ratismal.drivebackup.uploaders;

import java.io.IOException;

/**
 * Thrown to indicate an upload-related issue
 */
class UploadException extends IOException {
    /**
     * Constructs an {@code UploadException} with no
     * detail message.
     */
    public UploadException() {
    
    }

    /**
     * Constructs an {@code UploadException} with the
     * specified detail message.
     *
     * @param s the detail message.
     */
    public UploadException(String s) {
        super(s);
    }
}

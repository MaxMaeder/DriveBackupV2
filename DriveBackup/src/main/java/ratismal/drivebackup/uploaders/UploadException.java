package ratismal.drivebackup.uploaders;

/**
 * Thrown to indicate a upload-related issue
 */
class UploadException extends Exception {
    /**
     * Constructs an {@code UploadException} with no
     * detail message.
     */
    public UploadException() {
        super();
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
package ratismal.drivebackup.uploaders;

import ratismal.drivebackup.uploaders.Authenticator.AuthenticationProvider;

import java.io.IOException;

public interface Uploader {
    String getName();
    String getId();
    AuthenticationProvider getAuthProvider();
    boolean isAuthenticated();
    boolean isErrorWhileUploading();
    void test(java.io.File testFile);
    void uploadFile(java.io.File file, String type) throws IOException;
    void close();
}

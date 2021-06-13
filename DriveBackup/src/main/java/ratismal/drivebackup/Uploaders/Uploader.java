package ratismal.drivebackup.Uploaders;

import net.kyori.adventure.text.Component;

public interface Uploader {
    public String getName();
    public Component getSetupInstructions();
    public boolean isErrorWhileUploading();
    public void test(java.io.File testFile);
    public void uploadFile(java.io.File file, String type) throws Exception;
    public void close();
}
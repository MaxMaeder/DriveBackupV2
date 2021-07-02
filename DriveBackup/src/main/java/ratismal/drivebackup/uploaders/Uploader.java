package ratismal.drivebackup.uploaders;

import net.kyori.adventure.text.TextComponent;

public interface Uploader {
    public String getName();
    public TextComponent getSetupInstructions();
    public boolean isErrorWhileUploading();
    public void test(java.io.File testFile);
    public void uploadFile(java.io.File file, String type) throws Exception;
    public void close();
}
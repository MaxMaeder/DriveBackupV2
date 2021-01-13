package ratismal.drivebackup;

import net.kyori.text.TextComponent;

public interface Uploader {
    public String getName();
    public TextComponent getSetupInstructions();
    public boolean isErrorWhileUploading();
    public void uploadFile(java.io.File file, String type) throws Exception;
}
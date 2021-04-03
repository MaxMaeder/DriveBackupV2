package ratismal.drivebackup;

import net.kyori.adventure.text.Component;

public interface Uploader {
    public String getName();

    public Component getSetupInstructions();

    public boolean isErrorWhileUploading();

    public void uploadFile(java.io.File file, String type) throws Exception;

    public void close();
}
package ratismal.drivebackup.uploaders.webdav;

import com.github.sardine.impl.SardineException;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.configuration.ConfigurationSection;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.uploaders.UploadLogger;
import ratismal.drivebackup.util.ChunkedFileInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URL;
import java.util.UUID;

public final class NextcloudUploader extends WebDAVUploader {

    private static final String UPLOADER_NAME = "Nextcloud";
    private static final String ID = "nextcloud";
    
    private final int chunkSize;

    private String magic_upload_dir;

    public NextcloudUploader(DriveBackupInstance instance, UploadLogger logger) {
        super(instance, logger, true);
        setName(UPLOADER_NAME);
        setId(ID);
        ConfigurationSection config = instance.getConfigHandler().getConfig().getSection("nextcloud");
        chunkSize = config.getValue("chunk-size").getInt();
        try {
            findUploadDir();
        } catch (IOException e) {
            magic_upload_dir = null;
        }
    }

    private void findUploadDir() throws IOException {
        URL url = new URL(getHostname());
        StringBuilder host = new StringBuilder(url.toString());
        host = new StringBuilder(host.substring(0, host.indexOf(url.getPath())));
        if (sardine.exists(host + "/remote.php/dav/uploads/" + getUsername())) {
            magic_upload_dir = host + "/remote.php/dav/uploads/" + getUsername();
            return;
        }
        if (sardine.exists(host + "/uploads/" + getUsername())) {
            magic_upload_dir = host + "/uploads/" + getUsername();
            return;
        }
        String[] exploded = url.getPath().split("/");
        for (int i = 0; i < Array.getLength(exploded); i++) {
            host.append("/").append(exploded[i]);
            if (sardine.exists(host + "/uploads/" + getUsername())) {
                magic_upload_dir = host + "/uploads/" + getUsername();
                return;
            }
        }
    }

    @Override
    public void realUploadFile(@NotNull File file, @NotNull URL target) throws IOException {
        if (file.length() > chunkSize && magic_upload_dir != null) {
            String tempdir = magic_upload_dir + "/" + UUID.randomUUID();
            sardine.createDirectory(tempdir);
            try (FileInputStream _fis = new FileInputStream(file)) {
                ChunkedFileInputStream fis = new ChunkedFileInputStream(chunkSize, _fis);
                do {
                    sardine.put(tempdir + String.format("/%020d", fis.getCurrentOffset()), fis, null, true, fis.available());
                } while (fis.next());
                try {
                    sardine.move(tempdir + "/.file", target.toString());
                } catch (SardineException e) {
                    // Assume 504 Gateway Timeout means Nextcloud will succeed reassembling the
                    // file.
                    if (e.getStatusCode() != 504) {
                        throw e;
                    }
                }
            } catch (IOException e) {
                sardine.delete(tempdir);
                throw e;
            }
        } else {
            try (FileInputStream fis = new FileInputStream(file)) {
                sardine.put(target.toString(), fis, null, true, file.length());
            }
        }
    }
}

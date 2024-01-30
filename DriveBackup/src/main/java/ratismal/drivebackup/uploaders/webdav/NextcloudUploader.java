package ratismal.drivebackup.uploaders.webdav;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URL;
import java.util.UUID;

import com.github.sardine.impl.SardineException;

import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.UploadThread.UploadLogger;
import ratismal.drivebackup.config.configSections.BackupMethods.NextcloudBackupMethod;
import ratismal.drivebackup.util.ChunkedFileInputStream;

public class NextcloudUploader extends WebDAVUploader {

    public static String UPLOADER_NAME = "Nextcloud";

    private NextcloudBackupMethod nextcloud;

    private String magic_upload_dir;

    public NextcloudUploader(UploadLogger logger, NextcloudBackupMethod nextcloud) {
        super(logger, nextcloud);
        setName(UPLOADER_NAME);
        setId("nextcloud");
        this.nextcloud = nextcloud;
        try {
            findUploadDir();
        } catch (IOException e) {
            magic_upload_dir = null;
        }
    }

    private void findUploadDir() throws IOException {
        URL url = new URL(nextcloud.hostname);
        StringBuilder host = new StringBuilder(url.toString());
        host = new StringBuilder(host.substring(0, host.indexOf(url.getPath())));
        if (sardine.exists(host + "/remote.php/dav/uploads/" + nextcloud.username)) {
            magic_upload_dir = host + "/remote.php/dav/uploads/" + nextcloud.username;
            return;
        }
        if (sardine.exists(host + "/uploads/" + nextcloud.username)) {
            magic_upload_dir = host + "/uploads/" + nextcloud.username;
            return;
        }
        String[] exploded = url.getPath().split("/");
        for (int i = 0; i < Array.getLength(exploded); i++) {
            host.append("/").append(exploded[i]);
            if (sardine.exists(host + "/uploads/" + nextcloud.username)) {
                magic_upload_dir = host + "/uploads/" + nextcloud.username;
                return;
            }
        }
    }

    @Override
    public void realUploadFile(@NotNull File file, URL target) throws IOException {
        int chunksize = nextcloud.chunkSize;
        if (file.length() > chunksize && magic_upload_dir != null) {
            String tempdir = magic_upload_dir + "/" + UUID.randomUUID().toString();
            sardine.createDirectory(tempdir);
            try (FileInputStream _fis = new FileInputStream(file)) {
                ChunkedFileInputStream fis = new ChunkedFileInputStream(chunksize, _fis);
                do {
                    sardine.put(tempdir + String.format("/%020d", fis.getCurrentOffset()), fis, (String) null, true, fis.available());
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
                sardine.put(target.toString(), fis, (String) null, true, file.length());
            }
        }
    }
}

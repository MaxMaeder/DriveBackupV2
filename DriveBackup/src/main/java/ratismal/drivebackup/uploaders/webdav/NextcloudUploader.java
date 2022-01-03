package ratismal.drivebackup.uploaders.webdav;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URL;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.github.sardine.impl.SardineException;

import ratismal.drivebackup.UploadThread.UploadLogger;
import ratismal.drivebackup.config.configSections.BackupMethods.NextcloudBackupMethod;

public class NextcloudUploader extends WebDAVUploader {

    public static String UPLOADER_NAME = "Nextcloud";
    public static String UPLOADER_ID = "nextcloud";

    private NextcloudBackupMethod config;
    private UploadLogger logger;

    private String magic_upload_dir;

    public NextcloudUploader(UploadLogger logger, NextcloudBackupMethod webdav) {
        super(logger, webdav);
        config = webdav;

        try {
            findUploadDir();
        } catch (IOException e) {
            magic_upload_dir = null;
        }
    }

    private void findUploadDir() throws IOException {
        URL url = new URL(config.hostname);
        String host = url.toString();
        host = host.substring(0, host.indexOf(url.getPath()));

        if (sardine.exists(host + "/remote.php/dav/uploads/" + config.username)) {
            magic_upload_dir = host + "/remote.php/dav/uploads/" + config.username;
            return;
        }

        if (sardine.exists(host + "/uploads/" + config.username)) {
            magic_upload_dir = host + "/uploads/" + config.username;
            return;
        }

        String[] exploded = url.getPath().split("/");

        for (int i = 0; i<Array.getLength(exploded); i++) {
            host += "/" + exploded[i];
            if (sardine.exists(host + "/uploads/" + config.username)) {
                magic_upload_dir = host + "/uploads/" + config.username;
                return;
            }
        }
    }

    @Override
    public void realUploadFile(File file, URL target) throws IOException {
        int chunksize = config.chunkSize;
        if (file.length() > chunksize && magic_upload_dir != null) {
            String tempdir = magic_upload_dir + "/" + UUID.randomUUID().toString();
            sardine.createDirectory(tempdir);

            long size = file.length();
            long pos = 0;
            
            try (FileInputStream fis = new FileInputStream(file)) {
                while (pos < size) {
                    int this_size = (int)Long.min(size - pos, chunksize);
                    byte[] buff = new byte[chunksize];
                    int read_bytes = fis.read(buff, 0, this_size);

                    // Avoid zero padding
                    if (read_bytes < size) {
                        buff = Arrays.copyOf(buff, read_bytes);
                    }
                    SardineException ex = null;
                    for (int i=0; i<10; i++) {
                        try {
                            if (ex != null) {
                                logger.log("Error uploading fragment, retrying: " + ex.getMessage());
                            }
                            sardine.put(tempdir + String.format("/%020d-%020d", pos, pos+read_bytes-1), buff);
                            ex = null;
                            break;
                        } catch (SardineException e) {
                            ex = e;
                        }
                        try {
                            TimeUnit.SECONDS.sleep(1*i);
                        } catch (Exception e) {}
                    }
                    if (ex != null) {
                        throw ex;
                    }
                    pos += read_bytes;
                }
                try {
                    sardine.move(tempdir + "/.file", target.toString());
                } catch (SardineException e) {
                    // Assume 504 Gateway Timeout means Nextcloud will succeed reassembling the file.
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
                sardine.put(target.toString(), fis, (String)null, true, file.length());
            }
        }
    }

    /**
     * Gets the name of this upload service
     * @return name of upload service
     */
    @Override
    public String getName() {
        return UPLOADER_NAME;
    }

    /**
     * Gets the id of this upload service
     * @return id of upload service
     */
    @Override
    public String getId() {
        return UPLOADER_ID;
    }
}

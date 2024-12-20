package ratismal.drivebackup.uploaders.s3;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.UploadObjectArgs;
import io.minio.messages.Item;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.configuration.ConfigurationSection;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.uploaders.UploadLogger;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.util.NetUtil;

import java.io.File;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public final class S3Uploader extends Uploader {

    private static final String UPLOADER_NAME = "S3";
    private static final String UPLOADER_ID = "s3";

    private MinioClient minioClient;
    
    private String bucket;
    private String hostname;

    public S3Uploader(DriveBackupInstance instance, UploadLogger logger) {
        super(instance, UPLOADER_NAME, UPLOADER_ID, null, logger);
        ConfigurationSection section = instance.getConfigHandler().getConfig().getSection("s3");
        try {
            String endpoint = section.getValue("endpoint").getString();
            hostname = new URL(endpoint).getHost();
            bucket = section.getValue("bucket").getString();
            String accessKey = section.getValue("access-key").getString();
            String secretKey = section.getValue("secret-key").getString();
            minioClient = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        } catch (Exception e) {
            instance.getLoggingHandler().error("Failed to initialize S3 uploader", e);
            setErrorOccurred(true);
        }
    }

    @Override
    public void test(File testFile) {
        try {
            minioClient.uploadObject(UploadObjectArgs.builder().bucket(bucket).object(testFile.getName()).filename(testFile.getAbsolutePath()).build());
            Thread.sleep(5L);
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(testFile.getName()).build());
        } catch (Exception exception) {
            NetUtil.catchException(exception, hostname, logger);
            instance.getLoggingHandler().error("Failed to test S3 uploader", exception);
            setErrorOccurred(true);
        }
    }

    @Override
    public void uploadFile(File file, String type) {
        type = normalizeType(type);
        try {
            String key = type + "/" + file.getName();
            minioClient.uploadObject(UploadObjectArgs.builder().bucket(bucket).object(key).filename(file.getAbsolutePath()).build());
            try {
                pruneBackups(type);
            } catch (Exception e) {
                logger.log("backup-method-prune-failed");
                throw e;
            }
        } catch(Exception exception) {
            NetUtil.catchException(exception, hostname, logger);
            instance.getLoggingHandler().error("Failed to upload file to S3", exception);
            setErrorOccurred(true);
        }
    }

    @Override
    public void close() {
    }

    public void pruneBackups(String type) throws Exception {
        int fileLimit = getKeepCount();
        if (fileLimit == -1) {
            return;
        }
        TreeMap<ZonedDateTime, Item> files = getZipFiles(type);
        if (files.size() > fileLimit) {
            Map<String, String> placeholders = new HashMap<>(3);
            placeholders.put("file-count", String.valueOf(files.size()));
            placeholders.put("upload-method", getName());
            placeholders.put("file-limit", String.valueOf(fileLimit));
            logger.info("backup-method-limit-reached", placeholders);
            while (files.size() > fileLimit) {
                Map.Entry<ZonedDateTime, Item> firstEntry = files.firstEntry();
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(firstEntry.getValue().objectName()).build());
                files.remove(firstEntry.getKey());
            }
        }
    }

    @NotNull
    private TreeMap<ZonedDateTime, Item> getZipFiles(String type) throws Exception {
        type = normalizeType(type);
        String prefix = type + "/";
        TreeMap<ZonedDateTime, Item> files = new TreeMap<>();
        for (Result<Item> result : minioClient.listObjects(ListObjectsArgs.builder().bucket(bucket).prefix(prefix).build())) {
            Item item = result.get();
            files.put(item.lastModified(), item);
        }
        return files;
    }

    @NotNull
    private static String normalizeType(@NotNull String type) {
        if(type.startsWith("./")) {
            return type.substring(2);
        }
        return type;
    }
    
}

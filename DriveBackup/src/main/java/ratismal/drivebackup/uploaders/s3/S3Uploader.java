package ratismal.drivebackup.uploaders.s3;

import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import io.minio.errors.*;
import ratismal.drivebackup.UploadThread.UploadLogger;
import ratismal.drivebackup.config.configSections.BackupMethods.S3BackupMethod;
import ratismal.drivebackup.uploaders.Authenticator;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.NetUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class S3Uploader implements Uploader {
    private UploadLogger logger;

    public static String UPLOADER_NAME = "S3";
    public static String UPLOADER_ID = "s3";

    public MinioClient minioClient;

    private boolean _errorOccurred;
    private String _bucket;
    private String _hostname;

    public S3Uploader(UploadLogger logger, S3BackupMethod config) {
        this.logger = logger;

        try {
            _hostname = new URL(config.endpoint).getHost();
            _bucket = config.bucket;
            minioClient = MinioClient.builder().endpoint(config.endpoint).credentials(config.accessKey, config.secretKey).build();
        } catch(Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    @Override
    public String getName() {
        return UPLOADER_NAME;
    }

    @Override
    public String getId() {
        return UPLOADER_ID;
    }

    @Override
    public Authenticator.AuthenticationProvider getAuthProvider() {
        return null;
    }

    @Override
    public boolean isAuthenticated() {
        return true;
    }

    @Override
    public boolean isErrorWhileUploading() {
        return _errorOccurred;
    }

    @Override
    public void test(File testFile) {
        try {
            minioClient.uploadObject(UploadObjectArgs.builder().bucket(_bucket).object(testFile.getName()).filename(testFile.getAbsolutePath()).build());
        } catch (Exception exception) {
            NetUtil.catchException(exception, _hostname, logger);
            MessageUtil.sendConsoleException(exception);
            setErrorOccurred(true);
        }
    }

    @Override
    public void uploadFile(File file, String type) {
        if(type.startsWith("./")) type = type.substring(2);

        try {
            String key = type + "/" + file.getName();
            logger.log("key = " + key);
            minioClient.uploadObject(UploadObjectArgs.builder().bucket(_bucket).object(key).filename(file.getAbsolutePath()).build());
        } catch(Exception exception) {
            NetUtil.catchException(exception, _hostname, logger);
            MessageUtil.sendConsoleException(exception);
            setErrorOccurred(true);
        }
    }

    @Override
    public void close() {
    }

    public void setErrorOccurred(boolean errorOccurred) {
        _errorOccurred = errorOccurred;
    }
}

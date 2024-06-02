package ratismal.drivebackup;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.objects.Player;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.uploaders.UploadLogger;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.uploaders.dropbox.DropboxUploader;
import ratismal.drivebackup.uploaders.ftp.FTPUploader;
import ratismal.drivebackup.uploaders.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.uploaders.onedrive.OneDriveUploader;
import ratismal.drivebackup.uploaders.s3.S3Uploader;
import ratismal.drivebackup.uploaders.webdav.NextcloudUploader;
import ratismal.drivebackup.uploaders.webdav.WebDAVUploader;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.security.SecureRandom;

public final class TestThread implements Runnable {
    private final UploadLogger logger;
    private final String[] args;
    private final DriveBackupInstance instance;

    /**
     * Creates an instance of the {@code TestThread} object
     * @param instance the plugin instance
     * @param player the player who initiated the test
     * @param args any arguments that followed the command that initiated the test
     */
    @Contract (pure = true)
    public TestThread(DriveBackupInstance instance, Player player, String[] args) {
        this.instance = instance;
        this.args = args;
        logger = new UploadLogger(instance, player);
    }

    /**
     * Starts a test of a backup method
     */
    @Override
    public void run() {
        /*
         * Arguments:
         * 0) The backup method to test
         * 1) The name of the test file to upload during test
         * 2) The size (in bytes) of the file.
         */
        if (args.length < 2) {
            logger.log("test-method-not-specified");
            return;
        }
        String testFileName;
        if (args.length > 2) {
            testFileName = args[2];
        } else {
            testFileName = "testfile.txt";
        }
        int testFileSize;
        try {
            testFileSize = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            testFileSize = 1024;
        }
        String method = args[1];
        try {
            testUploadMethod(testFileName, testFileSize, method);
        } catch (Exception exception) {
            logger.log("test-method-invalid", "specified-method", method);
        }
    }

    /**
     * Tests a specific upload method
     * @param testFileName name of the test file to upload during the test
     * @param testFileSize the size (in bytes) of the file
     * @param method name of the upload method to test
     */
    private void testUploadMethod(String testFileName, int testFileSize, @NotNull String method) throws Exception {
        Config config = ConfigParser.getConfig();
        Uploader uploadMethod;
        switch (method) {
            case "googledrive":
                if (config.backupMethods.googleDrive.enabled) {
                    uploadMethod = new GoogleDriveUploader(instance, logger);
                } else {
                    sendMethodDisabled(logger, "GoogleDrive");
                    return;
                }
                break;
            case "onedrive":
                if (config.backupMethods.oneDrive.enabled) {
                    uploadMethod = new OneDriveUploader(instance, logger);
                } else {
                    sendMethodDisabled(logger, "OneDrive");
                    return;
                }
                break;
            case "dropbox":
                if (config.backupMethods.dropbox.enabled) {
                    uploadMethod = new DropboxUploader(instance, logger);
                } else {
                    sendMethodDisabled(logger, "Dropbox");
                    return;
                }
                break;
            case "webdav":
                if (config.backupMethods.webdav.enabled) {
                    uploadMethod = new WebDAVUploader(instance, logger, config.backupMethods.webdav);
                } else {
                    sendMethodDisabled(logger, "WebDAV");
                    return;
                }
                break;
            case "nextcloud":
                if (config.backupMethods.nextcloud.enabled) {
                    uploadMethod = new NextcloudUploader(instance, logger, config.backupMethods.nextcloud);
                } else {
                    sendMethodDisabled(logger, "Nextcloud");
                    return;
                }
                break;
            case "s3":
                if (config.backupMethods.s3.enabled) {
                    uploadMethod = new S3Uploader(instance, logger, config.backupMethods.s3);
                } else {
                    sendMethodDisabled(logger, "S3");
                    return;
                }
                break;
            case "ftp":
                if (config.backupMethods.ftp.enabled) {
                    uploadMethod = new FTPUploader(instance, logger, config.backupMethods.ftp);
                } else {
                    sendMethodDisabled(logger, "FTP");
                    return;
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid method");
        }
        logger.log("test-method-begin", "upload-method", uploadMethod.getName());
        String localTestFilePath = config.backupStorage.localDirectory + File.separator + testFileName;
        new File(config.backupStorage.localDirectory).mkdirs();
        try (FileOutputStream fos = new FileOutputStream(localTestFilePath)) {
            SecureRandom byteGenerator = new SecureRandom();
            byte[] randomBytes = new byte[testFileSize];
            byteGenerator.nextBytes(randomBytes);
            fos.write(randomBytes);
            fos.flush();
        } catch (Exception exception) {
            logger.log("test-file-creation-failed");
            instance.getLoggingHandler().error("Failed to create test file", exception);
        }
        File testFile = new File(localTestFilePath);
        uploadMethod.test(testFile);
        if (uploadMethod.didErrorOccur()) {
            logger.log("test-method-failed", "upload-method", uploadMethod.getName());
        } else {
            logger.log("test-method-successful", "upload-method", uploadMethod.getName());
        }
        uploadMethod.close();
        Files.delete(testFile.toPath());
    }

    private static void sendMethodDisabled(@NotNull UploadLogger logger, String methodName) {
        logger.log("test-method-not-enabled", "upload-method", methodName);
    }
    
}

package ratismal.drivebackup;

import org.bukkit.command.CommandSender;
import ratismal.drivebackup.UploadThread.UploadLogger;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;

import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.uploaders.dropbox.DropboxUploader;
import ratismal.drivebackup.uploaders.ftp.FTPUploader;
import ratismal.drivebackup.uploaders.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.uploaders.onedrive.OneDriveUploader;
import ratismal.drivebackup.uploaders.s3.S3Uploader;
import ratismal.drivebackup.uploaders.webdav.NextcloudUploader;
import ratismal.drivebackup.uploaders.webdav.WebDAVUploader;
import ratismal.drivebackup.util.MessageUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

import static ratismal.drivebackup.config.Localization.intl;

public class TestThread implements Runnable {
    private UploadLogger logger;
    private String[] args;

    /**
     * Creates an instance of the {@code TestThread} object
     * @param initiator the player who initiated the test
     * @param args any arguments that followed the command that initiated the test
     */
    public TestThread(CommandSender initiator, String[] args) {
        logger = new UploadLogger() {
            @Override
            public void log(String input, String... placeholders) {
                MessageUtil.Builder()
                    .mmText(input, placeholders)
                    .to(initiator)
                    .send();
            }

            @Override
            public void initiatorError(String input, String... placeholders) {
                MessageUtil.Builder()
                    .mmText(input, placeholders)
                    .to(initiator)
                    .toConsole(false)
                    .send();
            }
        };

        this.args = args;
    }

    /**
     * Starts a test of a backup method
     */
    @Override
    public void run() {

        /**
         * Arguments:
         * 0) The backup method to test
         * 1) The name of the test file to upload during test
         * 2) The size (in bytes) of the file.
         */

        if (args.length < 2) {
            logger.initiatorError(intl("test-method-not-specified"));

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
        } catch (Exception exception) {
            testFileSize = 1000;
        }
        
        String method = args[1];
        try {
            testUploadMethod(testFileName, testFileSize, method);
        } catch (Exception exception) {
            logger.initiatorError(intl("test-method-invalid"), "specified-method", method);
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
        Uploader uploadMethod = null;
        
        switch (method) {
            case "googledrive":
                if (config.backupMethods.googleDrive.enabled) {
                    uploadMethod = new GoogleDriveUploader(logger);
                } else {
                    sendMethodDisabled(logger, GoogleDriveUploader.UPLOADER_NAME);
                    return;
                }
                break;
            case "onedrive":
                if (config.backupMethods.oneDrive.enabled) {
                    uploadMethod = new OneDriveUploader(logger);
                } else {
                    sendMethodDisabled(logger, OneDriveUploader.UPLOADER_NAME);
                    return;
                }
                break;
            case "dropbox":
                if (config.backupMethods.dropbox.enabled) {
                    uploadMethod = new DropboxUploader(logger);
                } else {
                    sendMethodDisabled(logger, DropboxUploader.UPLOADER_NAME);
                    return;
                }
                break;
            case "webdav":
                if (config.backupMethods.webdav.enabled) {
                    uploadMethod = new WebDAVUploader(logger, config.backupMethods.webdav);
                } else {
                    sendMethodDisabled(logger, WebDAVUploader.UPLOADER_NAME);
                    return;
                }
                break;
            case "nextcloud":
                if (config.backupMethods.nextcloud.enabled) {
                    uploadMethod = new NextcloudUploader(logger, config.backupMethods.nextcloud);
                } else {
                    sendMethodDisabled(logger, NextcloudUploader.UPLOADER_NAME);
                    return;
                }
                break;
            case "s3":
                if (config.backupMethods.s3.enabled) {
                    uploadMethod = new S3Uploader(logger, config.backupMethods.s3);
                } else {
                    sendMethodDisabled(logger, S3Uploader.UPLOADER_NAME);
                    return;
                }
                break;
            case "ftp":
                if (config.backupMethods.ftp.enabled) {
                    uploadMethod = new FTPUploader(logger, config.backupMethods.ftp);
                } else {
                    sendMethodDisabled(logger, FTPUploader.UPLOADER_NAME);
                    return;
                }
                break;
            default:
                throw new Exception();
        }

        logger.log(
            intl("test-method-begin"), 
            "upload-method", uploadMethod.getName());

        String localTestFilePath = config.backupStorage.localDirectory + File.separator + testFileName;
        new File(config.backupStorage.localDirectory).mkdirs();

        try (FileOutputStream fos = new FileOutputStream(localTestFilePath)) {
            Random byteGenerator = new Random();
            
            byte[] randomBytes = new byte[testFileSize];
            byteGenerator.nextBytes(randomBytes);

            fos.write(randomBytes);
            fos.flush();
        } catch (Exception exception) {
            logger.log(intl("test-file-creation-failed"));
            MessageUtil.sendConsoleException(exception);
        }

        File testFile = new File(localTestFilePath);
        
        uploadMethod.test(testFile);

        if (uploadMethod.isErrorWhileUploading()) {
            logger.log(
                intl("test-method-failed"), 
                "upload-method", uploadMethod.getName());
        } else {
            logger.log(
                intl("test-method-successful"),
                "upload-method", uploadMethod.getName());
        }
        
        testFile.delete();
        uploadMethod.close();
    }

    private void sendMethodDisabled(@NotNull UploadLogger logger, String methodName) {
        logger.log(
            intl("test-method-not-enabled"), 
            "upload-method", methodName);
    }
}

package ratismal.drivebackup;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import ratismal.drivebackup.Uploaders.Uploader;
import ratismal.drivebackup.Uploaders.dropbox.DropboxUploader;
import ratismal.drivebackup.Uploaders.ftp.FTPUploader;
import ratismal.drivebackup.Uploaders.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.Uploaders.onedrive.OneDriveUploader;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.util.MessageUtil;

public class TestThread implements Runnable {
    private CommandSender initiator;
    private String[] args;

    /**
     * Creates an instance of the {@code TestThread} object
     * @param initiator the player who initiated the test
     * @param args any arguments that followed the command that initiated the test
     */
    public TestThread(CommandSender initiator, String[] args) {
        this.initiator = initiator;
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
         * 1) The name of the test file to upload during the test
         * 2) The size (in bytes) of the file
         */

        if (args.length < 2) {
            MessageUtil.sendMessage(initiator, "Please specify a backup method to test");

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

        try {
            testUploadMethod(testFileName, testFileSize, args[1]);
        } catch (Exception exception) {
            MessageUtil.sendMessage(initiator, args[1] + " isn't a valid backup method");
        }
    }

    /**
     * Tests a specific upload method
     * @param testFileName name of the test file to upload during the test
     * @param testFileSize the size (in bytes) of the file
     * @param method name of the upload method to test
     */
    private void testUploadMethod(String testFileName, int testFileSize, String method) throws Exception {

        Uploader uploadMethod = null;
        
        switch (method) {
            case "ftp":
                if (Config.isFtpEnabled()) {
                    uploadMethod = new FTPUploader();
                } else {
                    MessageUtil.sendMessage(initiator, "(S)FTP backups are disabled, you can enable them in the " + ChatColor.GOLD + "config.yml");
                    return;
                }
                break;
            case "googledrive":
                if (Config.isGoogleDriveEnabled()) {
                    uploadMethod = new GoogleDriveUploader();
                } else {
                    MessageUtil.sendMessage(initiator, "Google Drive backups are disabled, you can enable them in the " + ChatColor.GOLD + "config.yml");
                    return;
                }
                break;
            case "onedrive":
                if (Config.isOneDriveEnabled()) {
                    uploadMethod = new OneDriveUploader();
                } else {
                    MessageUtil.sendMessage(initiator, "OneDrive backups are disabled, you can enable them in the " + ChatColor.GOLD + "config.yml");
                    return;
                }
                break;
            case "dropbox":
                if (Config.isDropboxEnabled()) {
                    uploadMethod = new DropboxUploader();
                } else {
                    MessageUtil.sendMessage(initiator, "Dropbox backups are disabled, you can enable them in the " + ChatColor.GOLD + "config.yml");
                    return;
                }
                break;
            default:
                throw new Exception(method + " isn't a valid backup method");
        }

        MessageUtil.sendMessage(initiator, "Beginning the test on " + uploadMethod.getName());

        String localTestFilePath = Config.getDir() + File.separator + testFileName;
        new File(Config.getDir()).mkdirs();

        try (FileOutputStream fos = new FileOutputStream(localTestFilePath)) {
            Random byteGenerator = new Random();
            
            byte[] randomBytes = new byte[testFileSize];
            byteGenerator.nextBytes(randomBytes);

            fos.write(randomBytes);
            fos.flush();
        } catch (Exception exception) {
            MessageUtil.sendMessage(initiator, "Test file creation failed, please try again");
            MessageUtil.sendConsoleException(exception);
        }

        File testFile = new File(localTestFilePath);
        
        uploadMethod.test(testFile);

        if (uploadMethod.isErrorWhileUploading()) {
            MessageUtil.sendMessage(initiator, "The " + uploadMethod.getName() + " test was unsuccessful, please check the " + ChatColor.GOLD + "config.yml");
        } else {
            MessageUtil.sendMessage(initiator, "The " + uploadMethod.getName() + " test was successful");
        }
        
        testFile.delete();
    }
}
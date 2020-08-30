package ratismal.drivebackup;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.ftp.FTPUploader;
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

        switch (args[1]) {
            case "ftp": 
                testFtp(testFileName, testFileSize);
                break;
            default: MessageUtil.sendMessage(initiator, "\"" + args[1] + "\" isn't a supported backup method");
        }
    }

    /**
     * Tests the connection to the (S)FTP server
     * @param testFileName name of the test file to upload during the test
     * @param testFileSize the size (in bytes) of the file
     */
    private void testFtp(String testFileName, int testFileSize) {
        if (Config.isFtpEnabled()) {
            MessageUtil.sendMessage(initiator, "Beginning the (S)FTP connection and upload test");
        } else {
            MessageUtil.sendMessage(initiator, "(S)FTP backups are disabled, you can enable them in the " + ChatColor.GOLD + "config.yml");

            return;
        }

        FTPUploader ftpUploader = new FTPUploader();

        ftpUploader.testConnection(testFileName, testFileSize);

        if (ftpUploader.isErrorWhileUploading()) {
            MessageUtil.sendMessage(initiator, "The (S)FTP connection and upload test was unsuccessful, please check the server credentials in the " + ChatColor.GOLD + "config.yml");
        } else {
            MessageUtil.sendMessage(initiator, "The (S)FTP connection and upload test was successful");
        }
    }
}
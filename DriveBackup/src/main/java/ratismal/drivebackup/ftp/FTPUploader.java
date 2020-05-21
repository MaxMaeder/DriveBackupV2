package ratismal.drivebackup.ftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.util.MessageUtil;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

/**
 * Created by Ratismal on 2016-03-30.
 */

public class FTPUploader {
    private boolean errorOccurred;

    /**
     * Creates an instance of the {@code FTPUploader} object
     */
    public FTPUploader() {}

    /**
     * Uploads the specified file to the SFTP/FTP server inside a folder for the specified file type
     * <p>
     * The SFTP/FTP server credentials are specified by the user in the {@code config.yml}
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     */
    public void uploadFile(File file, String type) {
        try {

            if (Config.isFtpSftp()) {
                SFTPUploader.uploadFile(file, type);
                return;
            }

            FTPClient f = new FTPClient();
            if (Config.isFtpFtps()) {
                f = new FTPSClient();
            }
            f.connect(Config.getFtpHost(), Config.getFtpPort());
            f.login(Config.getFtpUser(), Config.getFtpPass());
            String baseDirectory = Config.getFtpDir();
            if (baseDirectory == null) {
                baseDirectory = f.printWorkingDirectory();
            }
            f.changeWorkingDirectory(baseDirectory);
            //f.changeWorkingDirectory("/");
            if (!f.changeWorkingDirectory(Config.getDestination())) {
                MessageUtil.sendConsoleMessage("Creating folder");
                f.makeDirectory(Config.getDestination());
                f.changeWorkingDirectory(Config.getDestination());
            }
            if (!f.changeWorkingDirectory(type)) {
                MessageUtil.sendConsoleMessage("Creating folder");
                f.makeDirectory(type);
                f.changeWorkingDirectory(type);
            }

            f.setFileType(FTP.BINARY_FILE_TYPE, FTP.BINARY_FILE_TYPE);
            f.setFileTransferMode(FTP.BINARY_FILE_TYPE);
            f.setListHiddenFiles(false);

            FileInputStream fs = new FileInputStream(file);
            f.storeFile(file.getName(), fs);
            fs.close();

            MessageUtil.sendConsoleMessage(f.printWorkingDirectory());

            deleteFiles(f, type);

            f.disconnect();


        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Gets whether an error occurred while accessing the authenticated user's OneDrive
     * @return whether an error occurred
     */
    public boolean isErrorWhileUploading() {
        return this.errorOccurred;
    }

    /**
     * Deletes the oldest files past the number to retain from the SFTP server inside the specified folder for the file type
     * <p>
     * The number of files to retain is specified by the user in the {@code config.yml}
     * @param f the FTPClient
     * @param type the type of file (ex. plugins, world)
     */
    private void deleteFiles(FTPClient f, String type) throws Exception {
        int fileLimit = Config.getKeepCount();
        if (fileLimit == -1) {
            return;
        }
        TreeMap<Date, FTPFile> files = processFiles(f);

        if (files.size() > fileLimit) {
            MessageUtil.sendConsoleMessage("There are " + files.size() + " file(s) which exceeds the " +
                    "limit of " + fileLimit + ", deleting.");
            while (files.size() > fileLimit) {
                f.deleteFile(files.firstEntry().getValue().getName());
                files.remove(files.firstKey());
            }
        }
    }

    /**
     * Returns a list of files inside the folder for the file type
     * @param f the FTPClient
     * @return the list of files
     */
    private TreeMap<Date, FTPFile> processFiles(FTPClient f) throws Exception {
        TreeMap<Date, FTPFile> result = new TreeMap<Date, FTPFile>();
        for (FTPFile file : f.mlistDir()) {
            if (file.getName().endsWith(".zip"))
                result.put(file.getTimestamp().getTime(), file);
        }
        return result;
    }

    /**
     * Sets whether an error occurred while accessing the authenticated user's OneDrive
     * @param errorOccurredValue whether an error occurred
     */
    private void setErrorOccurred(boolean errorOccurredValue) {
        this.errorOccurred = errorOccurredValue;
    }
}

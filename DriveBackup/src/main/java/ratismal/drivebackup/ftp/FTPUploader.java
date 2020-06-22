package ratismal.drivebackup.ftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.util.MessageUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * Created by Ratismal on 2016-03-30.
 */

public class FTPUploader {
    private FTPClient ftpClient;
    private SFTPUploader sftpClient;

    private boolean _errorOccurred;

    private String initialRemoteFolder;
    private String localBaseFolder;
    private String remoteBaseFolder;

    /**
     * Creates an instance of the {@code FTPUploader} object using the server credentials specified by the user in the {@code config.yml}
     */
    public FTPUploader() {
        try {
            if (Config.isFtpSftp()) {
                sftpClient = new SFTPUploader();
            } else {
                connect(Config.getFtpHost(), Config.getFtpPort(), Config.getFtpUser(), Config.getFtpPass(), Config.isFtpFtps());
            }

            localBaseFolder = ".";
            if (Config.getFtpDir() == null) {
                remoteBaseFolder = Config.getDestination();
            } else {
                remoteBaseFolder = Config.getFtpDir() + File.separator + Config.getDestination();
            }
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Creates an instance of the {@code FTPUploader} object using the specified credentials
     * @param host the hostname of the FTP server
     * @param port the port
     * @param username the username
     * @param password the password (leave blank if none)
     * @param ftps whether FTP using SSL
     * @param sftp whether FTP using SSH
     * @param publicKey the path to the SSH public key, relative to the "DriveBackupV2 folder" (leave blank if none)
     * @param passphrase the SSH public key passphrase (leave blank if none)
     * @param localBaseFolder the path to the folder which all local file paths are relative to
     * @param remoteBaseFolder the path to the folder which all remote file paths are relative to 
     */
    public FTPUploader(String host, int port, String username, String password, boolean ftps, boolean sftp, String publicKey, String passphrase, String localBaseFolder, String remoteBaseFolder) {
        try {
            if (sftp) {
                sftpClient = new SFTPUploader(host, port, username, password, publicKey, passphrase, localBaseFolder, remoteBaseFolder);
            } else {
                connect(host, port, username, password, ftps);
            }

            this.localBaseFolder = localBaseFolder;
            this.remoteBaseFolder = remoteBaseFolder;
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }        
    }

    /**
     * Authenticates with a server via FTP
     * @param host the hostname of the FTP server
     * @param port the port
     * @param username the username
     * @param password the password (leave blank if none)
     * @param ftps whether FTP using SSL
     * @throws Exception
     */
    private void connect(String host, int port, String username, String password, boolean ftps) throws Exception {
        ftpClient = new FTPClient();
        if (ftps) {
            ftpClient = new FTPSClient();
        }

        ftpClient.connect(host, port);
        ftpClient.login(username, password);

        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE, FTP.BINARY_FILE_TYPE);
        ftpClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
        ftpClient.setListHiddenFiles(false);

        initialRemoteFolder = ftpClient.printWorkingDirectory();
    }

    /**
     * Closes the connection to the (S)FTP server
     */
    public void close() {
        try {
            if (sftpClient != null) {
                sftpClient.close();
            }
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Uploads the specified file to the (S)FTP server inside a folder for the specified file type
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     */
    public void uploadFile(File file, String type) {
        try {
            type = type.replace(".."  + File.separator, "");

            if (sftpClient != null) {
                sftpClient.uploadFile(file, type);
                return;
            }

            resetWorkingDirectory();
            createThenEnter(remoteBaseFolder);
            createThenEnter(type);

            FileInputStream fs = new FileInputStream(file);
            ftpClient.storeFile(file.getName(), fs);
            fs.close();

            deleteFiles(type);

            ftpClient.disconnect();
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Downloads the specifed file from the (S)FTP server into a folder for the specified file type
     * @param filePath the path of the file
     * @param type the type of file (ex. plugins, world)
     */
    public void downloadFile(String filePath, String type) {
        try {
            if (sftpClient != null) {
                sftpClient.downloadFile(filePath, type);
                return;
            }

            resetWorkingDirectory();
            ftpClient.changeWorkingDirectory(remoteBaseFolder);

            File outputFile = new File(localBaseFolder + File.separator + type);
            if (!outputFile.exists()) {
                outputFile.mkdirs();
            }

            OutputStream outputStream = new FileOutputStream(localBaseFolder + File.separator + type + File.separator + new File(filePath).getName());
            ftpClient.retrieveFile(filePath, outputStream);

            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Returns a list of the paths of the files inside the specified folder and subfolders on the (S)FTP server
     * @param folderPath the path of the folder
     * @return the list of file paths
     */
    public ArrayList<String> getFiles(String folderPath) {
        ArrayList<String> filePaths = new ArrayList<>();

        try {
            if (sftpClient != null) {
                return sftpClient.getFiles(folderPath);
            }

            resetWorkingDirectory();
            ftpClient.changeWorkingDirectory(remoteBaseFolder);
            ftpClient.changeWorkingDirectory(folderPath);

            for (FTPFile file : ftpClient.mlistDir()) {
                if (file.isDirectory()) {
                    // file.getName() = file path
                    filePaths.addAll(prependToAll(getFiles(file.getName()), new File(file.getName()).getName() + File.separator));
                } else {
                    filePaths.add(file.getName());
                }
            }
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }

        return filePaths;
    }

    /**
     * Returns a list of the paths of the ZIP files and their modification dates inside the specified folder on the (S)FTP server
     * @param folderPath the path of the folder
     * @return the list of files
     */
    public HashMap<String, Date> getZipFiles(String folderPath) {
        HashMap<String, Date> filePaths = new HashMap<>();

        try {
            if (sftpClient != null) {
                return sftpClient.getZipFiles(folderPath);
            }

            resetWorkingDirectory();
            ftpClient.changeWorkingDirectory(remoteBaseFolder);
            ftpClient.changeWorkingDirectory(folderPath);

            for (FTPFile file : ftpClient.mlistDir()) {
                if (file.getName().endsWith(".zip")) {
                    filePaths.put(file.getName(), file.getTimestamp().getTime());
                }
            }
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }

        return filePaths;
    }

    /**
     * Gets whether an error occurred while accessing the (S)FTP server
     * @return whether an error occurred
     */
    public boolean isErrorWhileUploading() {
        return this._errorOccurred;
    }

    /**
     * Deletes the oldest files past the number to retain from the FTP server inside the specified folder for the file type
     * <p>
     * The number of files to retain is specified by the user in the {@code config.yml}
     * @param type the type of file (ex. plugins, world)
     * @throws Exception
     */
    private void deleteFiles(String type) throws Exception {
        int fileLimit = Config.getKeepCount();
        if (fileLimit == -1) {
            return;
        }
        TreeMap<Date, FTPFile> files = getZipFiles();

        if (files.size() > fileLimit) {
            MessageUtil.sendConsoleMessage("There are " + files.size() + " file(s) which exceeds the limit of " + fileLimit + ", deleting");
            while (files.size() > fileLimit) {
                ftpClient.deleteFile(files.firstEntry().getValue().getName());
                files.remove(files.firstKey());
            }
        }
    }

    /**
     * Returns a list of ZIP files and their modification dates inside the current working directory
     * @return the list of files
     * @throws Exception
     */
    private TreeMap<Date, FTPFile> getZipFiles() throws Exception {
        TreeMap<Date, FTPFile> files = new TreeMap<>();

        for (FTPFile file : ftpClient.mlistDir()) {
            if (file.getName().endsWith(".zip"))
                files.put(file.getTimestamp().getTime(), file);
        }

        return files;
    }

    /**
     * Creates a folder with the specified path inside the current working directory, then enters it
     * @param parentFolder the parent folder
     * @param path the relative path of the folder to create
     * @throws Exception
     */
    private void createThenEnter(String path) throws Exception {
        if (!ftpClient.changeWorkingDirectory(path)) {
            ftpClient.makeDirectory(path);
            ftpClient.changeWorkingDirectory(path);
        }
    }

    /**
     * Resets the current working directory to what it was when connection to the SFTP server was established
     * @throws Exception
     */
    private void resetWorkingDirectory() throws Exception {
        ftpClient.changeWorkingDirectory(initialRemoteFolder);
    }

    /**
     * Replaces any file seperators in the specified path with the configured file seperator
     * @param path the file path
     * @return the file path with replaced seperators
     */
    private static String replaceFileSeperators(String path) {
        return path.replace("/", Config.getFtpFileSeperator()).replace("\\", Config.getFtpFileSeperator());
    }

    /**
     * Prepends the specified String to each element in the specified ArrayList
     * @param list the ArrayList
     * @param string the String
     * @return the new ArrayList
     */
    private static ArrayList<String> prependToAll(ArrayList<String> list, String string) {
        for (int i = 0; i < list.size(); i++) {
            list.set(i, string + list.get(i));
        }

        return list;
    }

    /**
     * Sets whether an error occurred while accessing the FTP server
     * @param errorOccurred whether an error occurred
     */
    private void setErrorOccurred(boolean errorOccurred) {
        _errorOccurred = errorOccurred;
    }
}

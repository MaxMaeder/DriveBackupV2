package ratismal.drivebackup.uploaders.ftp;

import com.google.api.client.util.Strings;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.config.configSections.BackupMethods.FTPBackupMethod;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.uploaders.UploadLogger;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.util.NetUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by Ratismal on 2016-03-30.
 */

public final class FTPUploader extends Uploader {

    private static final String UPLOADER_NAME = "(S)FTP";
    private static final String ID = "ftp";
    
    private FTPClient ftpClient;
    private SFTPUploader sftpClient;

    private String initialRemoteFolder;
    private String localBaseFolder;
    private String remoteBaseFolder;
    private String host;

    /**
     * Returns the configured FTP file separator
     * @return the separator
     */
    @Contract (pure = true)
    private String sep() {
        return instance.getConfigHandler().getConfig().getValue("advanced", "file-separator").getString();
    }

    /**
     * Creates an instance of the {@code FTPUploader} object using the server credentials specified by the user in the {@code config.yml}
     */
    public FTPUploader(DriveBackupInstance instance, UploadLogger logger, FTPBackupMethod ftp) {
        super(instance, UPLOADER_NAME, ID, null, logger);
        try {
            if (ftp.sftp) {
                sftpClient = new SFTPUploader(logger);
            } else {
                connect(ftp.hostname, ftp.port, ftp.username, ftp.password, ftp.ftps);
                host = ftp.hostname;
            }
            localBaseFolder = ".";
            if (Strings.isNullOrEmpty(ftp.remoteDirectory)) {
                remoteBaseFolder = ftp.remoteDirectory;
            } else {
                remoteBaseFolder = ftp.remoteDirectory + sep() + ftp.remoteDirectory;
            }
        } catch (Exception e) {
            instance.getLoggingHandler().error("backup-method-ftp-connection-failed", e);
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
     * @param localBaseFolder the path to the folder, which all local file paths are relative to.
     * @param remoteBaseFolder the path to the folder, which all remote file paths are relative to.
     */
    public FTPUploader(DriveBackupInstance instance, UploadLogger logger, String host, int port, String username, String password, boolean ftps, boolean sftp, String publicKey, String passphrase, String localBaseFolder, String remoteBaseFolder) {
        super(instance, UPLOADER_NAME, ID, null, logger);
        try {
            if (sftp) {
                setId("sftp");
                sftpClient = new SFTPUploader(logger, host, port, username, password, publicKey, passphrase, localBaseFolder, remoteBaseFolder);
            } else {
                connect(host, port, username, password, ftps);
                this.host = host;
            }
            this.localBaseFolder = localBaseFolder;
            this.remoteBaseFolder = remoteBaseFolder;
        } catch (Exception e) {
            instance.getLoggingHandler().error("backup-method-ftp-connection-failed", e);
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
            setId("ftps");
            ftpClient = new FTPSClient();
        }
        ftpClient.setConnectTimeout(10 * 1000);
        ftpClient.setDefaultTimeout(30 * 1000);
        ftpClient.setDataTimeout(30 * 1000);
        ftpClient.setControlKeepAliveTimeout(30L * 1000L);
        ftpClient.connect(host, port);
        ftpClient.login(username, password);
        ftpClient.enterLocalPassiveMode();
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE, FTP.BINARY_FILE_TYPE);
        ftpClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
        ftpClient.setListHiddenFiles(false);
        initialRemoteFolder = ftpClient.printWorkingDirectory();
    }
    
    @Override
    public boolean isAuthenticated() {
        if (sftpClient != null) {
            return sftpClient.isAuthenticated();
        } else {
            return ftpClient.isConnected();
        }
    }

    /**
     * Closes the connection to the (S)FTP server
     */
    public void close() {
        try {
            if (sftpClient != null) {
                sftpClient.close();
            } else {
                ftpClient.disconnect();
            }
        } catch (Exception e) {
            instance.getLoggingHandler().error("backup-method-ftp-close-failed", e);
            setErrorOccurred(true);
        }
    }

    /**
     * Tests the connection to the (S)FTP server by connecting and uploading a small file.
     * @param testFile the file to upload
     */
    public void test(File testFile) {
        try {
            if (sftpClient != null) {
                sftpClient.test(testFile);
                return;
            }
            try (FileInputStream fis = new FileInputStream(testFile)) {
                resetWorkingDirectory();
                createThenEnter(remoteBaseFolder);
                ftpClient.storeFile(testFile.getName(), fis);
                TimeUnit.SECONDS.sleep(5L);
                ftpClient.deleteFile(testFile.getName());
            }
        } catch (Exception e) {
            NetUtil.catchException(e, host, logger);
            instance.getLoggingHandler().error("backup-method-ftp-test-failed", e);
            setErrorOccurred(true);
        }
    }

    /**
     * Uploads the specified file to the (S)FTP server inside a folder for the specified file type.
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     */
    public void uploadFile(File file, String type) {
        try {
            type = type.replace(".."  + sep(), "");
            if (sftpClient != null) {
                sftpClient.uploadFile(file, type);
                return;
            }
            resetWorkingDirectory();
            createThenEnter(remoteBaseFolder);
            createThenEnter(type);
            try (FileInputStream fs = new FileInputStream(file)) {
                ftpClient.storeFile(file.getName(), fs);
            }
            try {
                pruneBackups();
            } catch (Exception e) {
                logger.log("backup-method-prune-failed");
                throw e;
            }
        } catch (Exception e) {
            NetUtil.catchException(e, host, logger);
            instance.getLoggingHandler().error("backup-method-ftp-connection-failed", e);
            setErrorOccurred(true);
        }
    }

    /**
     * Downloads the specifed file from the (S)FTP server into a folder for the specified file type.
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
            File outputFile = new File(localBaseFolder + sep() + type);
            if (!outputFile.exists()) {
                outputFile.mkdirs();
            }
            OutputStream outputStream = Files.newOutputStream(Paths.get(localBaseFolder + "/" + type + "/" + new File(filePath).getName()));
            ftpClient.retrieveFile(filePath, outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            instance.getLoggingHandler().error("backup-method-ftp-download-failed", e);
            setErrorOccurred(true);
        }
    }

    /**
     * Returns a list of the paths of the files inside the specified folder and subfolders.
     * @param folderPath the path of the folder
     * @return the list of file paths
     */
    public @NotNull List<String> getFiles(String folderPath) {
        List<String> filePaths = new ArrayList<>(10);
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
                    filePaths.addAll(prependToAll(getFiles(file.getName()), new File(file.getName()).getName() + sep()));
                } else {
                    filePaths.add(file.getName());
                }
            }
        } catch (Exception e) {
            instance.getLoggingHandler().error("backup-method-ftp-get-file-list-failed", e);
            setErrorOccurred(true);
        }
        return filePaths;
    }

    /**
     * Deletes the oldest files past the number to retain from the FTP server inside the specified folder for the file type.
     * <p>
     * The number of files to retain is specified by the user in the {@code config.yml}
     * @throws Exception
     */
    private void pruneBackups() throws Exception {
        int fileLimit = getKeepCount();
        if (-1 == fileLimit) {
            return;
        }
        TreeMap<Instant, FTPFile> files = getZipFiles();
        if (files.size() > fileLimit) {
            Map<String, String> placeholders = new HashMap<>(3);
            placeholders.put("upload-method", getName());
            placeholders.put("file-count", String.valueOf(files.size()));
            placeholders.put("file-limit", String.valueOf(fileLimit));
            logger.info("backup-method-limit-reached", placeholders);
            while (files.size() > fileLimit) {
                ftpClient.deleteFile(files.firstEntry().getValue().getName());
                files.remove(files.firstKey());
            }
        }
    }

    /**
     * Returns a list of ZIP files, and their modification dates inside the current working directory.
     * @return a map of ZIP files, and their modification dates
     * @throws Exception
     */
    @NotNull
    private TreeMap<Instant, FTPFile> getZipFiles() throws Exception {
        TreeMap<Instant, FTPFile> files = new TreeMap<>();
        for (FTPFile file : ftpClient.mlistDir()) {
            if (file.getName().endsWith(".zip")) {
                files.put(file.getTimestamp().getTime().toInstant(), file);
            }
        }
        return files;
    }

    /**
     * Creates a folder with the specified path inside the current working directory, then enters it.
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
     * Resets the current working directory to what it was when connection to the SFTP server was established.
     * @throws Exception
     */
    private void resetWorkingDirectory() throws Exception {
        ftpClient.changeWorkingDirectory(initialRemoteFolder);
    }

    /**
     * Prepends the specified String to each element in the specified ArrayList.
     * @param list the ArrayList
     * @param string the String
     * @return the new ArrayList
     */
    @Contract ("_, _ -> param1")
    private static @NotNull List<String> prependToAll(@NotNull List<String> list, String string) {
        list.replaceAll(s -> string + s);
        return list;
    }
}

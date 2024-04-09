package ratismal.drivebackup.uploaders.ftp;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.UploadThread.UploadLogger;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.config.configSections.BackupMethods.FTPBackupMethod;
import ratismal.drivebackup.plugin.DriveBackup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.google.api.client.util.Strings;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.StatefulSFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.password.*;
import net.schmizz.sshj.userauth.method.AuthMethod;
import net.schmizz.sshj.userauth.method.AuthPassword;
import net.schmizz.sshj.userauth.method.AuthPublickey;

import static ratismal.drivebackup.config.Localization.intl;

/**
 * Created by Ratismal on 2016-03-30.
 */

public class SFTPUploader {
    private UploadLogger logger;

    private SSHClient sshClient;
    private StatefulSFTPClient sftpClient;

    private String initialRemoteFolder;
    private String _localBaseFolder;
    private String _remoteBaseFolder;

    /**
     * Creates an instance of the {@code SFTPUploader} object using the server credentials specified by the user in the {@code config.yml}
     * @throws Exception
     */
    public SFTPUploader(UploadLogger logger) throws Exception {
        this.logger = logger;
        Config config = ConfigParser.getConfig();
        FTPBackupMethod ftp = config.backupMethods.ftp;
        connect(ftp.hostname, ftp.port, ftp.username, ftp.password, ftp.publicKey, ftp.passphrase);
        _localBaseFolder = ".";
        if (Strings.isNullOrEmpty(ftp.remoteDirectory)) {
            _remoteBaseFolder = config.backupStorage.remoteDirectory;
        } else {
            _remoteBaseFolder = ftp.remoteDirectory + "/" + config.backupStorage.remoteDirectory;
        }
    }

    /**
     * Creates an instance of the {@code SFTPUploader} object using the specified credentials
     * @param host the hostname of the SFTP server
     * @param port the port
     * @param username the username
     * @param password the password (leave blank if none)
     * @param publicKey the path to the public key, relative to the "DriveBackupV2 folder" (leave blank if none)
     * @param passphrase the public key passphrase (leave blank if none)
     * @param localBaseFolder the path to the folder, which all local file paths are relative to.
     * @param remoteBaseFolder the path to the folder, which all remote file paths are relative to.
     * @throws Exception
     */
    public SFTPUploader(UploadLogger logger, String host, int port, String username, String password, String publicKey, String passphrase, String localBaseFolder, String remoteBaseFolder) throws Exception {
        this.logger = logger;
        connect(host, port, username, password, publicKey, passphrase);
        _localBaseFolder = localBaseFolder;
        _remoteBaseFolder = remoteBaseFolder;
    }

    /**
     * Authenticates with a SFTP server using the specified credentials
     * @param host the hostname of the SFTP server
     * @param port the port
     * @param username the username
     * @param password the password (leave blank if none)
     * @param publicKey the path to the public key, relative to the "DriveBackupV2 folder" (leave blank if none)
     * @param passphrase the public key passphrase (leave blank if none)
     * @throws Exception
     */
    private void connect(String host, int port, String username, final String password, String publicKey, String passphrase) throws Exception {
        sshClient = new SSHClient();
        // Disable host checking
        sshClient.addHostKeyVerifier(new PromiscuousVerifier());
        sshClient.connect(host, port);
        ArrayList<AuthMethod> sshAuthMethods = new ArrayList<>();
        if (!Strings.isNullOrEmpty(password)) {
            sshAuthMethods.add(new AuthPassword(new PasswordFinder() {
                @Override
                public char[] reqPassword(Resource<?> resource) {
                    return password.toCharArray();
                }

                @Override
                public boolean shouldRetry(Resource<?> resource) {
                    return false;
                }
            }));
        }
        if (!Strings.isNullOrEmpty(publicKey)) {
            if (!Strings.isNullOrEmpty(passphrase)) {
                sshAuthMethods.add(new AuthPublickey(sshClient.loadKeys(
                        DriveBackup.getInstance().getDataFolder().getAbsolutePath() + "/" + publicKey,
                        passphrase.toCharArray())));
            } else {
                sshAuthMethods.add(new AuthPublickey(sshClient.loadKeys(
                    DriveBackup.getInstance().getDataFolder().getAbsolutePath() + "/" + publicKey)));
            }
        }
        sshClient.auth(username, sshAuthMethods);
        sftpClient = new StatefulSFTPClient(sshClient.newSFTPClient().getSFTPEngine());
        initialRemoteFolder = sftpClient.pwd();
    }

    public boolean isAuthenticated() {
        return sshClient.isConnected();
    }

    /**
     * Closes the connection to the SFTP server
     * @throws Exception
     */
    public void close() throws Exception {
        sshClient.close();
    }

    /**
     * Tests the connection to the (S)FTP server by connecting and uploading a small file.
     * @param testFile the file to upload
     * @throws Exception
     */
    public void test(File testFile) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            resetWorkingDirectory();
            createThenEnter(_remoteBaseFolder);
            sftpClient.put(testFile.getAbsolutePath(), testFile.getName());
            TimeUnit.SECONDS.sleep(5);
            sftpClient.rm(testFile.getName());
        } catch (Exception exception) {
            throw exception;
        }
    }

    /**
     * Uploads the specified file to the SFTP server inside a folder for the specified file type.
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     * @throws Exception
     */
    public void uploadFile(File file, String type) throws Exception {
        resetWorkingDirectory();
        createThenEnter(_remoteBaseFolder);
        createThenEnter(type);
        sftpClient.put(file.getAbsolutePath(), file.getName());
        try {
            pruneBackups();
        } catch (Exception e) {
            logger.log(intl("backup-method-prune-failed"));
            throw e;
        }
    }

    /**
     * Downloads the specified file from the SFTP server into a folder for the specified file type.
     * @param filePath the path of the file
     * @param type the type of file (ex. plugins, world)
     * @throws Exception
     */
    public void downloadFile(String filePath, String type) throws Exception {
        resetWorkingDirectory();
        sftpClient.cd(_remoteBaseFolder);
        File outputFile = new File(_localBaseFolder + "/" + type);
        if (!outputFile.exists()) {
            outputFile.mkdirs();
        }
        sftpClient.get(filePath, _localBaseFolder + "/" + type + "/" + new File(filePath).getName());
    }

    /**
     * Returns a list of the paths of the files inside the specified folder and any subfolders.
     * @param type the type of folder (ex. plugins, world)
     * @return the list of file paths
     * @throws Exception
     */
    public ArrayList<String> getFiles(String type) throws Exception {
        ArrayList<String> result = new ArrayList<>();
        resetWorkingDirectory();
        sftpClient.cd(_remoteBaseFolder);
        sftpClient.cd(type);
        for (RemoteResourceInfo file : sftpClient.ls()) {
            if (file.isDirectory()) {
                result.addAll(prependToAll(getFiles(file.getPath()), file.getName() + "/"));
            } else {
                result.add(file.getName());
            }
        }
        return result;
    }

    /**
     * Deletes the oldest files past the number to retain from the SFTP server inside the current working directory.
     * <p>
     * The number of files to retain is specified by the user in the {@code config.yml}
     * @throws Exception
     */
    private void pruneBackups() throws Exception {
        int fileLimit = ConfigParser.getConfig().backupStorage.keepCount;
        if (fileLimit == -1) {
            return;
        }
        TreeMap<Date, RemoteResourceInfo> files = getZipFiles();
        if (files.size() > fileLimit) {
            logger.info(
                intl("backup-method-limit-reached"), 
                "file-count", String.valueOf(files.size()),
                "upload-method", "(S)FTP",
                "file-limit", String.valueOf(fileLimit));
            while (files.size() > fileLimit) {
                sftpClient.rm(files.firstEntry().getValue().getName());
                files.remove(files.firstEntry().getKey());
            }
        }
    }

    /**
     * Returns a list of ZIP files, and their modification dates inside the current working directory.
     * @return a map of the files and their modification dates
     * @throws Exception
     */
    @NotNull
    private TreeMap<Date, RemoteResourceInfo> getZipFiles() throws Exception {
        TreeMap<Date, RemoteResourceInfo> files = new TreeMap<>();
        for (RemoteResourceInfo file : sftpClient.ls()) {
            if (file.getName().endsWith(".zip")) {
                files.put(new Date(file.getAttributes().getMtime()), file);
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
        try {
            sftpClient.cd(path);
        } catch (Exception error) {
            sftpClient.mkdirs(path);
            sftpClient.cd(path);
        }
    }

    /**
     * Resets the current working directory to what it was when connection to the SFTP server was established.
     * @throws IOException
     */
    private void resetWorkingDirectory() throws IOException {
        sftpClient.cd(initialRemoteFolder);
    }

    /**
     * Prepends the specified String to each element in the specified ArrayList.
     * @param list the ArrayList
     * @param string the String
     * @return the new ArrayList
     */
    @Contract ("_, _ -> param1")
    private static ArrayList<String> prependToAll(@NotNull ArrayList<String> list, String string) {
        for (int i = 0; i < list.size(); i++) {
            list.set(i, string + list.get(i));
        }
        return list;
    }
}

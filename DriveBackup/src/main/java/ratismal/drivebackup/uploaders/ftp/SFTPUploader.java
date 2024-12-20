package ratismal.drivebackup.uploaders.ftp;

import com.google.api.client.util.Strings;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.StatefulSFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.method.AuthMethod;
import net.schmizz.sshj.userauth.method.AuthPassword;
import net.schmizz.sshj.userauth.method.AuthPublickey;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.Resource;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.uploaders.UploadLogger;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by Ratismal on 2016-03-30.
 */

public final class SFTPUploader {
    private final UploadLogger logger;
    private final DriveBackupInstance instance;

    private SSHClient sshClient;
    private StatefulSFTPClient sftpClient;

    private String initialRemoteFolder;
    private final String localBaseFolder;
    private final String remoteBaseFolder;

    /**
     * Creates an instance of the {@code SFTPUploader} object using the server credentials specified by the user in the {@code config.yml}
     * @throws Exception
     */
    public SFTPUploader(DriveBackupInstance instance, UploadLogger logger, FTPUploader ftpUploader) throws Exception {
        this.instance = instance;
        this.logger = logger;
        FTPDetails ftpDetails = FTPDetails.load(instance);
        connect(ftpDetails.getHost(), ftpDetails.getPort(), ftpDetails.getUsername(), ftpDetails.getPassword(), ftpDetails.getPublicKey(), ftpDetails.getPassphrase());
        localBaseFolder = ".";
        if (Strings.isNullOrEmpty(ftpDetails.getDirectory())) {
            remoteBaseFolder = ftpUploader.getRemoteSaveDirectory();
        } else {
            remoteBaseFolder = ftpDetails.getDirectory() + ftpUploader.sep() + ftpUploader.getRemoteSaveDirectory();
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
    public SFTPUploader(DriveBackupInstance instance, UploadLogger logger, String host, int port, String username, String password, String publicKey, String passphrase, String localBaseFolder, String remoteBaseFolder) throws Exception {
        this.instance = instance;
        this.logger = logger;
        connect(host, port, username, password, publicKey, passphrase);
        this.localBaseFolder = localBaseFolder;
        this.remoteBaseFolder = remoteBaseFolder;
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
    private void connect(String host, int port, String username, String password, String publicKey, String passphrase) throws Exception {
        sshClient = new SSHClient();
        // Disable host checking
        sshClient.addHostKeyVerifier(new PromiscuousVerifier());
        sshClient.connect(host, port);
        Collection<AuthMethod> sshAuthMethods = new ArrayList<>(2);
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
                        instance.getDataDirectory() + "/" + publicKey,
                        passphrase.toCharArray())));
            } else {
                sshAuthMethods.add(new AuthPublickey(sshClient.loadKeys(
                        instance.getDataDirectory() + "/" + publicKey)));
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
    public void close() throws IOException {
        sshClient.close();
    }

    /**
     * Tests the connection to the (S)FTP server by connecting and uploading a small file.
     * @param testFile the file to upload
     * @throws Exception
     */
    public void test(@NotNull File testFile) throws Exception {
        resetWorkingDirectory();
        createThenEnter(remoteBaseFolder);
        sftpClient.put(testFile.getAbsolutePath(), testFile.getName());
        TimeUnit.SECONDS.sleep(5L);
        sftpClient.rm(testFile.getName());
    }

    /**
     * Uploads the specified file to the SFTP server inside a folder for the specified file type.
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     * @throws Exception
     */
    public void uploadFile(@NotNull File file, String type) throws Exception {
        resetWorkingDirectory();
        createThenEnter(remoteBaseFolder);
        createThenEnter(type);
        sftpClient.put(file.getAbsolutePath(), file.getName());
        try {
            pruneBackups();
        } catch (Exception e) {
            logger.log("backup-method-prune-failed");
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
        sftpClient.cd(remoteBaseFolder);
        File outputFile = new File(localBaseFolder + "/" + type);
        if (!outputFile.exists()) {
            outputFile.mkdirs();
        }
        sftpClient.get(filePath, localBaseFolder + "/" + type + "/" + new File(filePath).getName());
    }

    /**
     * Returns a list of the paths of the files inside the specified folder and any subfolders.
     *
     * @param type the type of folder (ex. plugins, world)
     *
     * @return the list of file paths
     *
     * @throws Exception
     */
    public @NotNull List<String> getFiles(String type) throws Exception {
        List<String> result = new ArrayList<>();
        resetWorkingDirectory();
        sftpClient.cd(remoteBaseFolder);
        sftpClient.cd(type);
        for (RemoteResourceInfo file : sftpClient.ls()) {
            if (file.isDirectory()) {
                result.addAll(FTPUploader.prependToAll(getFiles(file.getPath()), file.getName() + "/"));
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
        int fileLimit = instance.getConfigHandler().getConfig().getValue("keep-count").getInt();
        if (fileLimit == -1) {
            return;
        }
        TreeMap<Instant, RemoteResourceInfo> files = getZipFiles();
        if (files.size() > fileLimit) {
            Map<String, String> placeholders = new HashMap<>(3);
            placeholders.put("file-count", String.valueOf(files.size()));
            placeholders.put("upload-method", "(S)FTP");
            placeholders.put("file-limit", String.valueOf(fileLimit));
            logger.info("backup-method-limit-reached", placeholders);
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
    private TreeMap<Instant, RemoteResourceInfo> getZipFiles() throws Exception {
        TreeMap<Instant, RemoteResourceInfo> files = new TreeMap<>();
        for (RemoteResourceInfo file : sftpClient.ls()) {
            if (file.getName().endsWith(".zip")) {
                files.put(Instant.ofEpochMilli(file.getAttributes().getMtime()), file);
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
        } catch (Exception e) {
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
    
}

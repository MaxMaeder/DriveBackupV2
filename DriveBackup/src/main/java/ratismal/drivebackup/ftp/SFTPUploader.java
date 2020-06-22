package ratismal.drivebackup.ftp;

import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.util.MessageUtil;

import java.io.File;
import java.util.*;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.StatefulSFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.password.*;
import net.schmizz.sshj.userauth.method.AuthMethod;
import net.schmizz.sshj.userauth.method.AuthPassword;
import net.schmizz.sshj.userauth.method.AuthPublickey;

/**
 * Created by Ratismal on 2016-03-30.
 */

public class SFTPUploader {
    private SSHClient sshClient;
    private StatefulSFTPClient sftpClient;

    private String initialRemoteFolder;
    private String localBaseFolder;
    private String remoteBaseFolder;

    /**
     * Creates an instance of the {@code SFTPUploader} object using the server credentials specified by the user in the {@code config.yml}
     * @throws Exception
     */
    public SFTPUploader() throws Exception {
        connect(Config.getFtpHost(), Config.getFtpPort(), Config.getFtpUser(), Config.getFtpPass(), Config.getSftpPublicKey(), Config.getSftpPass());

        localBaseFolder = ".";
        if (Config.getFtpDir() == null) {
            remoteBaseFolder = Config.getDestination();
        } else {
            remoteBaseFolder = Config.getFtpDir() + File.separator + Config.getDestination();
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
     * @param localBaseFolder the path to the folder which all local file paths are relative to
     * @param remoteBaseFolder the path to the folder which all remote file paths are relative to 
     * @throws Exception
     */
    public SFTPUploader(String host, int port, String username, String password, String publicKey, String passphrase, String localBaseFolder, String remoteBaseFolder) throws Exception {
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
    private void connect(String host, int port, String username, final String password, String publicKey, String passphrase) throws Exception {
        sshClient = new SSHClient();
        sshClient.addHostKeyVerifier(new PromiscuousVerifier()); // Disable host checking
        sshClient.connect(host, port);

        ArrayList<AuthMethod> sshAuthMethods = new ArrayList<>();

        if (password != null) {
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

        if (publicKey != null) {
            if (passphrase != null) {
                sshAuthMethods.add(new AuthPublickey(sshClient.loadKeys(
                        DriveBackup.getInstance().getDataFolder().getAbsolutePath() + File.separator + publicKey,
                        passphrase.toCharArray())));
            } else {
                sshAuthMethods.add(new AuthPublickey(sshClient.loadKeys(
                    DriveBackup.getInstance().getDataFolder().getAbsolutePath() + File.separator + publicKey)));
            }
        }

        sshClient.auth(username, sshAuthMethods);
        sftpClient = new StatefulSFTPClient(sshClient.newSFTPClient().getSFTPEngine());

        initialRemoteFolder = sftpClient.pwd();
    }

    /**
     * Closes the connection to the SFTP server
     * @throws Exception
     */
    public void close() throws Exception {
        sshClient.close();
    }

    /**
     * Uploads the specified file to the SFTP server inside a folder for the specified file type
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     * @throws Exception
     */
    public void uploadFile(File file, String type) throws Exception {
        resetWorkingDirectory();
        createThenEnter(remoteBaseFolder);
        createThenEnter(type);

        sftpClient.put(file.getAbsolutePath(), file.getName());
        
        deleteFiles();
    }

    /**
     * Downloads the specified file from the SFTP server into a folder for the specified file type
     * @param filePath the path of the file
     * @param type the type of file (ex. plugins, world)
     * @throws Exception
     */
    public void downloadFile(String filePath, String type) throws Exception {
        resetWorkingDirectory();
        sftpClient.cd(remoteBaseFolder);

        File outputFile = new File(localBaseFolder + File.separator + type);
        if (!outputFile.exists()) {
            outputFile.mkdirs();
        }

        sftpClient.get(filePath, localBaseFolder + File.separator + type + File.separator + new File(filePath).getName());
    }

    /**
     * Returns a list of the paths of the files inside the specified folder and subfolders on the SFTP server
     * @param type the type of folder (ex. plugins, world)
     * @return the list of file paths
     * @throws Exception
     */
    public ArrayList<String> getFiles(String type) throws Exception {
        ArrayList<String> result = new ArrayList<>();

        resetWorkingDirectory();
        sftpClient.cd(remoteBaseFolder);
        sftpClient.cd(type);

        for (RemoteResourceInfo file : sftpClient.ls()) {
            if (file.isDirectory()) {
                result.addAll(prependToAll(getFiles(file.getPath()), file.getName() + File.separator));
            } else {
                result.add(file.getName());
            }
        }

        return result;
    }

    /**
     * Returns a list of the paths of the ZIP files and their modification dates inside the specified folder on the SFTP server
     * @param folderPath the path of the folder
     * @return the list of files
     * @throws Exception
     */
    public HashMap<String, Date> getZipFiles(String folderPath) throws Exception {
        HashMap<String, Date> filePaths = new HashMap<>();

        resetWorkingDirectory();
        sftpClient.cd(remoteBaseFolder);
        sftpClient.cd(folderPath);

        for (RemoteResourceInfo file : sftpClient.ls()) {
            if (file.getName().endsWith(".zip")) {
                filePaths.put(file.getName(), new Date(file.getAttributes().getMtime()));
            }
        }

        return filePaths;
    }

    /**
     * Deletes the oldest files past the number to retain from the SFTP server inside the current working directory
     * <p>
     * The number of files to retain is specified by the user in the {@code config.yml}
     * @throws Exception
     */
    private void deleteFiles() throws Exception {
        int fileLimit = Config.getKeepCount();
        if (fileLimit == -1) {
            return;
        }
        TreeMap<Date, RemoteResourceInfo> files = getZipFiles();

        if (files.size() > fileLimit) {
            MessageUtil.sendConsoleMessage("There are " + files.size() + " file(s) which exceeds the limit of " + fileLimit + ", deleting");

            while (files.size() > fileLimit) {
                sftpClient.rm(files.firstEntry().getValue().getName());
                files.remove(files.firstEntry().getKey());
            }
        }
    }

    /**
     * Returns a list of ZIP files and their modification dates inside the current working directory
     * @return the list of files
     * @throws Exception
     */
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
     * Creates a folder with the specified path inside the current working directory, then enters it
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
     * Resets the current working directory to what it was when connection to the SFTP server was established
     * @throws Exception
     */
    private void resetWorkingDirectory() throws Exception {
        sftpClient.cd(initialRemoteFolder);
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
}

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

    /**
     * Uploads the specified file to the SFTP server inside a folder for the
     * specified file type
     * <p>
     * The SFTP server credentials are specified by the user in the
     * {@code config.yml}
     * 
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     */
    public static void uploadFile(File file, String type) throws Exception {

        SSHClient sshClient = new SSHClient();
        sshClient.addHostKeyVerifier(new PromiscuousVerifier()); // Disable host checking
        sshClient.connect(Config.getFtpHost(), Config.getFtpPort());

        List<AuthMethod> sshAuthMethods = new ArrayList<AuthMethod>();

        if (Config.getFtpPass() != null) {
            sshAuthMethods.add(new AuthPassword(new PasswordFinder() {
                @Override
                public char[] reqPassword(Resource<?> resource) {
                    return Config.getFtpPass().toCharArray();
                }

                @Override
                public boolean shouldRetry(Resource<?> resource) {
                    return false;
                }
            }));
        }

        if (Config.getSftpPublicKey() != null) {
            if (Config.getSftpPass() != null) {
                sshAuthMethods.add(new AuthPublickey(sshClient.loadKeys(
                        DriveBackup.getInstance().getDataFolder().getAbsolutePath() + File.separator + Config.getSftpPublicKey(),
                        Config.getSftpPass().toCharArray())));
            } else {
                sshAuthMethods.add(new AuthPublickey(sshClient.loadKeys(
                    DriveBackup.getInstance().getDataFolder().getAbsolutePath() + File.separator + Config.getSftpPublicKey())));
            }
        }

        sshClient.auth(Config.getFtpUser(), sshAuthMethods);
        StatefulSFTPClient remoteFolder = new StatefulSFTPClient(sshClient.newSFTPClient().getSFTPEngine());

        if (Config.getFtpDir() != null) {
            createThenEnter(remoteFolder, Config.getFtpDir());
        }
        createThenEnter(remoteFolder, Config.getDestination());
        createThenEnter(remoteFolder, type);

        remoteFolder.put(file.getAbsolutePath(), file.getName());
        
        deleteFiles(remoteFolder);

        sshClient.close();
  }

    /**
     * Deletes the oldest files past the number to retain from the SFTP server inside the folder for the file type
     * <p>
     * The number of files to retain is specified by the user in the {@code config.yml}
     * @param remoteFolder the folder
     */
    public static void deleteFiles(StatefulSFTPClient remoteFolder) throws Exception {
        int fileLimit = Config.getKeepCount();
        if (fileLimit == -1) {
            return;
        }
        TreeMap<Date, RemoteResourceInfo> files = processFiles(remoteFolder);

        if (files.size() > fileLimit) {
            MessageUtil.sendConsoleMessage("There are " + files.size() + " file(s) which exceeds the limit of " + fileLimit + ", deleting.");

            while (files.size() > fileLimit) {
                remoteFolder.rm(files.firstEntry().getValue().getName());
                files.remove(files.firstEntry().getKey());
            }
        }
    }

    /**
     * Returns a list of files inside the folder for the file type
     * @param remoteFolder the folder
     * @return the list of files
     */
    public static TreeMap<Date, RemoteResourceInfo> processFiles(StatefulSFTPClient remoteFolder) throws Exception {
        TreeMap<Date, RemoteResourceInfo> result = new TreeMap<Date, RemoteResourceInfo>();

        for (RemoteResourceInfo file : remoteFolder.ls()) {
            if (file.getName().split(".").length == 2 && file.getName().split(".") [1] == "zip") {
                result.put(new Date(file.getAttributes().getMtime()), file);
            }
        }
        return result;
    }

    /**
     * Creates a folder with the specified name inside the specifed parent folder, then enters it
     * @param parentFolder the parent folder
     * @param name the name of the folder to create
     */
    public static void createThenEnter(StatefulSFTPClient parentFolder, String name) throws Exception {
        try {
            parentFolder.cd(name);
        } catch (Exception error) {
            parentFolder.mkdir(name);
            parentFolder.cd(name);
        }
    }
}

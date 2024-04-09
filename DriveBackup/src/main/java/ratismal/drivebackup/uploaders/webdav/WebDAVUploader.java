package ratismal.drivebackup.uploaders.webdav;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.uploaders.Authenticator.AuthenticationProvider;
import ratismal.drivebackup.UploadThread.UploadLogger;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.configSections.BackupMethods.WebDAVBackupMethod;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.NetUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

import static ratismal.drivebackup.config.Localization.intl;

public class WebDAVUploader extends Uploader {

    Sardine sardine;
    private URL _remoteBaseFolder;
    public static final String UPLOADER_NAME = "WebDAV";

    /**
     * Creates an instance of the {@code WebDAVUploader} object using the server credentials specified by the user in the {@code config.yml}
     */
    public WebDAVUploader(UploadLogger logger, WebDAVBackupMethod webdav) {
        super(UPLOADER_NAME, "webdav");
        this.logger = logger;
        try {
            _remoteBaseFolder = new URL(webdav.hostname + "/" + webdav.remoteDirectory);
            sardine = SardineFactory.begin(webdav.username, webdav.password);
            sardine.enablePreemptiveAuthentication(_remoteBaseFolder.getHost());
            createDirectory(_remoteBaseFolder.toString());
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
        setAuthenticated(true);
        setAuthProvider(null);
    }

    /**
     * Closes the connection to the WebDAV server
     */
    public void close() {
        try {
            sardine.shutdown();
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Tests the connection to the WebDAV server by connecting and uploading a small file.
     * @param testFile the file to upload
     */
    public void test(File testFile) {
        try {
            URL target = new URL(_remoteBaseFolder + "/" + testFile.getName());
            realUploadFile(testFile, target);
            TimeUnit.SECONDS.sleep(5);
            sardine.delete(target.toString());
        } catch (Exception exception) {
            NetUtil.catchException(exception, _remoteBaseFolder.getHost(), logger);
            MessageUtil.sendConsoleException(exception);
            setErrorOccurred(true);
        }
    }

    public void realUploadFile(@NotNull File file, @NotNull URL target) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            sardine.put(target.toString(), fis, (String)null, true, file.length());
        }
    }

    /**
     * Uploads the specified file to the WebDAV server inside a folder for the specified file type.
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     */
    public void uploadFile(File file, String type) {
        try {
            type = type.replaceAll(".{1,2}[/\\\\]", "");
            createDirectory(_remoteBaseFolder.toString() + "/" + type);
            URL target = new URL(_remoteBaseFolder + "/" + type + "/" + file.getName());
            realUploadFile(file, target);
            try {
                pruneBackups(type);
            } catch (Exception e) {
                logger.log(intl("backup-method-prune-failed"));
                
                throw e;
            }
        } catch (Exception exception) {
            NetUtil.catchException(exception, _remoteBaseFolder.getHost(), logger);
            MessageUtil.sendConsoleException(exception);
            setErrorOccurred(true);
        }
    }

    /**
     * Returns a list of the paths of the files inside the specified folder and subfolders.
     * @param folderPath the path of the folder
     * @return the list of file paths
     */
    public ArrayList<String> getFiles(String folderPath) {
        ArrayList<String> filePaths = new ArrayList<>();
        try {
            //TODO path
            List<DavResource> resources = sardine.list(new URL(_remoteBaseFolder + "/" + folderPath).toString());
            for (DavResource resource : resources) {
                if (resource.isDirectory()) {
                    filePaths.addAll(prependToAll(getFiles(resource.getName()), new File(resource.getName()).getName() + '/'));
                } else {
                    filePaths.add(resource.getName());
                }
            }
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
        return filePaths;
    }

    /**
     * Deletes the oldest files past the number to retain from the FTP server inside the specified folder for the file type.
     * <p>
     * The number of files to retain is specified by the user in the {@code config.yml}
     * @param type the type of file (ex. plugins, world)
     * @throws Exception
     */
    public void pruneBackups(String type) throws Exception {
        int fileLimit = ConfigParser.getConfig().backupStorage.keepCount;
        if (fileLimit == -1) {
            return;
        }
        TreeMap<Date, DavResource> files = getZipFiles(type);
        if (files.size() > fileLimit) {
            logger.info(
                intl("backup-method-limit-reached"), 
                "file-count", String.valueOf(files.size()),
                "upload-method", getName(),
                "file-limit", String.valueOf(fileLimit));
            while (files.size() > fileLimit) {
                sardine.delete(new URL(_remoteBaseFolder + "/" + type + "/" + files.firstEntry().getValue().getName()).toString());
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
    private TreeMap<Date, DavResource> getZipFiles(String type) throws Exception {
        TreeMap<Date, DavResource> files = new TreeMap<>();
        List<DavResource> resources = sardine.list(new URL(_remoteBaseFolder + "/" + type).toString());
        for (DavResource resource : resources) {
            if (resource.getName().endsWith(".zip")) {
                files.put(resource.getModified(), resource);
            }
        }
        return files;
    }

    private String rstrip(@NotNull String src, char remove) {
        while (src.charAt(src.length()-1) == remove) {
            src = src.substring(0, src.length()-2);
        }
        return src;
    }

    /**
     * Creates a folder with the specified path inside the current working directory, then enters it.
     * @param path the relative path of the folder to create
     */
    private void createDirectory(String path) {
        path = rstrip(path, '/');
        try {
            if (!sardine.exists(path)) {
                int li = path.lastIndexOf('/');
                if (li > 0) {
                    String parent = path.substring(0, li);
                    if (!sardine.exists(parent)) {
                        createDirectory(parent);
                    }
                }
                sardine.createDirectory(path);
            }
        } catch (IOException exception) {
            //Sardine throws an error when the file exists instead of returning a boolean.
        }
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

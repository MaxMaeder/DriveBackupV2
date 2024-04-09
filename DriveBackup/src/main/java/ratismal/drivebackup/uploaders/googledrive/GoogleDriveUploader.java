package ratismal.drivebackup.uploaders.googledrive;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.*;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.uploaders.Authenticator;
import ratismal.drivebackup.uploaders.Obfusticate;
import ratismal.drivebackup.uploaders.Authenticator.AuthenticationProvider;
import ratismal.drivebackup.UploadThread.UploadLogger;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.NetUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.json.JSONObject;

import static ratismal.drivebackup.config.Localization.intl;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class GoogleDriveUploader extends Uploader {
    
    public static final String APPLICATION_VND_GOOGLE_APPS_FOLDER = "application/vnd.google-apps.folder";
    private String refreshToken;

    /**
     * A cached instance of shared drives
     */
    private List<com.google.api.services.drive.model.Drive> drives;

    public static final String UPLOADER_NAME = "Google Drive";

    /**
     * A global instance of the HTTP transport
     */
    private static final HttpTransport httpTransport = new NetHttpTransport();

    /**
     * A global instance of the JSON factory
     */
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    /**
     * Global Google Drive API client
     */
    private Drive service;


    /**
     * Creates an instance of the {@code GoogleDriveUploader} object
     */
    public GoogleDriveUploader(UploadLogger logger) {
        super(UPLOADER_NAME, "googledrive");
        this.logger = logger;
        setAuthProvider(AuthenticationProvider.GOOGLE_DRIVE);
        try {
            refreshToken = Authenticator.getRefreshToken(AuthenticationProvider.GOOGLE_DRIVE);
            retrieveNewAccessToken();
            drives = service.drives().list().execute().getItems();
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Gets a new Google Drive access token for the authenticated user.
     */
    private void retrieveNewAccessToken() throws Exception {
        RequestBody requestBody = new FormBody.Builder()
            .add("client_id", Obfusticate.decrypt(AuthenticationProvider.GOOGLE_DRIVE.getClientId()))
            .add("client_secret", Obfusticate.decrypt(AuthenticationProvider.GOOGLE_DRIVE.getClientSecret()))
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build();
        Request request = new Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(requestBody)
            .build();
        Response response = DriveBackup.httpClient.newCall(request).execute();
        JSONObject parsedResponse = new JSONObject(response.body().string());
        response.close();
        if (!response.isSuccessful()) {
            return;
        }
        service = new Drive.Builder(
            httpTransport, 
            JSON_FACTORY, 
            setTimeout(new Credential(
                BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(parsedResponse.getString("access_token"))))
            .setApplicationName("DriveBackupV2")
            .build();
    }

    @Override
    public boolean isAuthenticated() {
        return service != null;
    }

    /**
     * Sets the connect/read timeouts of the Google Drive Client by implementing {@code HttpRequestInitializer}
     * @param requestInitializer the default {@code HttpRequestInitializer} provided by the Google Drive Client
     * @return the modified {@code HttpRequestInitializer} with the connect/read timeouts set 
     */
    @NotNull
    @Contract (value = "_ -> new", pure = true)
    private static HttpRequestInitializer setTimeout(final HttpRequestInitializer requestInitializer) {
        return httpRequest -> {
            requestInitializer.initialize(httpRequest);
            // 1 minute connect timeout
            httpRequest.setConnectTimeout(1 * 60000);
            // 4 hours read timeout
            httpRequest.setReadTimeout(4 * 60 * 60000);
        };
    }

    /**
     * Tests the Google Drive account by uploading a small file
     * @param testFile the file to upload during the test
     */
    public void test(java.io.File testFile) {
        try {
            String sharedDriveId = ConfigParser.getConfig().backupMethods.googleDrive.sharedDriveId;
            String destination = ConfigParser.getConfig().backupStorage.remoteDirectory;
            File body = new File();
            body.setTitle(testFile.getName());
            body.setDescription("DriveBackupV2 test file");
            FileContent testContent = new FileContent("plain/txt", testFile);
            File folder;
            if (!sharedDriveId.isEmpty()) {
                folder = createFolder(destination, sharedDriveId);
            } else {
                folder = createFolder(destination);
            }
            ParentReference fileParent = new ParentReference();
            fileParent.setId(folder.getId());
            body.setParents(Collections.singletonList(fileParent));
            File uploadedFile = service.files().insert(body, testContent).setSupportsAllDrives(true).execute();
            String fileId = uploadedFile.getId();
            TimeUnit.SECONDS.sleep(5);
            service.files().delete(fileId).setSupportsAllDrives(true).execute();
        } catch (Exception exception) {
            NetUtil.catchException(exception, "www.googleapis.com", logger);
            MessageUtil.sendConsoleException(exception);
            setErrorOccurred(true);
        }
    }

    /**
     * Uploads the specified file to the authenticated user's Google Drive inside a folder for the specified file type.
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     */
    public void uploadFile(java.io.File file, String type) {
        try {
            String sharedDriveId = ConfigParser.getConfig().backupMethods.googleDrive.sharedDriveId;
            String destination = ConfigParser.getConfig().backupStorage.remoteDirectory;
            retrieveNewAccessToken();
            ArrayList<String> typeFolders = new ArrayList<>();
            Collections.addAll(typeFolders, destination.split("[/\\\\]"));
            Collections.addAll(typeFolders, type.split("[/\\\\]"));
            File folder = null;
            for (String typeFolder : typeFolders) {
                if (typeFolder.equals(".") || typeFolder.equals("..")) {
                    continue;
                }
                if (folder == null && !sharedDriveId.isEmpty()) {
                    folder = createFolder(typeFolder, sharedDriveId);
                } else if (folder == null) {
                    folder = createFolder(typeFolder);
                } else if (!sharedDriveId.isEmpty()) {
                    folder = createFolder(typeFolder, folder, true);
                } else {
                    folder = createFolder(typeFolder, folder, false);
                }
            }
            File fileMetadata = new File();
            fileMetadata.setTitle(file.getName());
            fileMetadata.setDescription("Uploaded by the DriveBackupV2 Minecraft plugin");
            fileMetadata.setMimeType("application/zip");
            ParentReference fileParent = new ParentReference();
            fileParent.setId(folder.getId());
            fileMetadata.setParents(Collections.singletonList(fileParent));
            FileContent fileContent = new FileContent("application/zip", file);
            service.files().insert(fileMetadata, fileContent).setSupportsAllDrives(true).execute();
            try {
                pruneBackups(folder);
            } catch (Exception e) {
                if (!sharedDriveId.isEmpty()) {
                    logger.log(intl("backup-method-shared-drive-prune-failed"));
                } else {
                    logger.log(intl("backup-method-prune-failed"));
                }
                throw e;
            }
        } catch (Exception exception) {
            NetUtil.catchException(exception, "www.googleapis.com", logger);
            MessageUtil.sendConsoleException(exception);
            setErrorOccurred(true);
        }
    }

    /**
     * Closes any remaining connections retrieveNewAccessToken
     */
    public void close() {
        // nothing needs to be done
    }

    /**
     * Setup for authenticated user that has access to one or more shared drives.
     * @throws Exception
     */
    public void setupSharedDrives(CommandSender initiator) throws Exception {
        if (drives != null && !drives.isEmpty()) {
            logger.log(intl("google-pick-shared-drive"));
            logger.log(
                intl("google-shared-drive-option"),
                "select-command", "1",
                "drive-num", "1",
                "drive-name", intl("default-google-drive-name"));
            int index = 1;
            for (com.google.api.services.drive.model.Drive drive : drives) {
                logger.log(
                    intl("google-shared-drive-option"),
                    "select-command", drive.getId(),
                    "drive-num", String.valueOf(++index),
                    "drive-name", drive.getName()); 
            }
            if (initiator instanceof Player) {
                Player player = (Player) initiator;
                DriveBackup.chatInputPlayers.add(player);
            } else {
                DriveBackup.chatInputPlayers.add(initiator);
            }
        } else {
            Authenticator.linkSuccess(initiator, getAuthProvider(), logger);
        }
    }

    public void finalizeSharedDrives(CommandSender initiator, String input) {
        DriveBackup instance = DriveBackup.getInstance();
        final String idKey = "googledrive.shared-drive-id";
        for (com.google.api.services.drive.model.Drive drive : drives) {
            if (input.equals(drive.getId())) {
                instance.getConfig().set(idKey, input);
                instance.saveConfig();
                Authenticator.linkSuccess(initiator, getAuthProvider(), logger);
                return;
            }
        }
        if ("1".equals(input)) {
            instance.getConfig().set(idKey, "");
            instance.saveConfig();
            Authenticator.linkSuccess(initiator, getAuthProvider(), logger);
            return;
        } else if (input.matches("[0-9]+")) {
            instance.getConfig().set(idKey, drives.get(Integer.parseInt(input) - 2).getId());
            instance.saveConfig();
            Authenticator.linkSuccess(initiator, getAuthProvider(), logger);
                        
            return;
        }
        // TODO: handle this better
        logger.log(intl("link-provider-failed"), "provider", getAuthProvider().getName());
    }

    /**
     * Creates a folder with the specified name in the specified parent folder in the authenticated user's Google Drive.
     * @param name the name of the folder
     * @param parent the parent folder
     * @return the created folder
     * @throws Exception
     */
    private File createFolder(String name, File parent, boolean sharedDrive) throws Exception {
        File folder = null;
        folder = getFolder(name, parent, sharedDrive);
        if (folder != null) {
            return folder;
        }
        ParentReference parentReference = new ParentReference();
        parentReference.setId(parent.getId());
        folder = new File();
        folder.setTitle(name);
        folder.setMimeType(APPLICATION_VND_GOOGLE_APPS_FOLDER);
        folder.setParents(Collections.singletonList(parentReference));
        folder = service.files().insert(folder).setSupportsAllDrives(true).execute();
        return folder;
    }

    /**
     * Creates a folder with the specified name in the authenticated user's specified Shared Drive.
     * @param name the name of the folder
     * @param driveId the parent folder
     * @return the created folder
     * @throws Exception
     */
    private File createFolder(String name, String driveId) throws Exception {
        File folder = null;
        folder = getFolder(name, driveId);
        if (folder != null) {
            return folder;
        }
        ParentReference parentReference = new ParentReference();
        parentReference.setId(driveId);
        folder = new File();
        folder.setTitle(name);
        folder.setMimeType(APPLICATION_VND_GOOGLE_APPS_FOLDER);
        folder.setParents(Collections.singletonList(parentReference));
        folder = service.files().insert(folder).setSupportsAllDrives(true).execute();
        return folder;
    }

    /**
     * Creates a folder with the specified name in the root of the authenticated user's Google Drive.
     * @param name the name of the folder
     * @return the created folder
     * @throws Exception
     */
    private File createFolder(String name) throws Exception {
        File folder = null;
        folder = getFolder(name);
        if (folder != null) {
            return folder;
        }
        folder = new File();
        folder.setTitle(name);
        folder.setMimeType(APPLICATION_VND_GOOGLE_APPS_FOLDER);
        folder = service.files().insert(folder).execute();
        return folder;
    }

    /**
     * Returns the folder in the specified Shared Drive of the authenticated user's Google Drive with the specified name.
     * @param name the name of the folder
     * @param driveId the ID of the drive to use
     * @return the folder or {@code null}
     */
    @Nullable
    private File getFolder(String name, String driveId) {
        try {
            Drive.Files.List request = service.files().list()
                .setDriveId(driveId)
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .setCorpora("drive")
                .setQ("mimeType='application/vnd.google-apps.folder' and trashed=false and '" + driveId + "' in parents");
            FileList files = request.execute();
            for (File folderfiles : files.getItems()) {
                if (folderfiles.getTitle().equals(name)) {
                    return folderfiles;
                }
            }
            return null;
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
        }
        return null;
    }

    /**
     * Returns the folder in the specified parent folder of the authenticated user's Google Drive with the specified name.
     * @param name the name of the folder
     * @param parent the parent folder
     * @return the folder or {@code null}
     */
    @Nullable
    private File getFolder(String name, File parent, boolean sharedDrive) {
        try {
            Drive.Files.List request = service.files().list()
                .setQ("mimeType='application/vnd.google-apps.folder' and trashed=false and '" + parent.getId() + "' in parents");
            if (sharedDrive) {
                request.setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .setCorpora("allDrives");
            }
            FileList files = request.execute();
            for (File folderfiles : files.getItems()) {
                if (folderfiles.getTitle().equals(name)) {
                    return folderfiles;
                }
            }
            return null;
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
        }
        return null;
    }

    /**
     * Returns the folder in the root of the authenticated user's Google Drive with the specified name.
     * @param name the name of the folder
     * @return the folder or {@code null}
     */
    @Nullable
    private File getFolder(String name) {
        try {
            Drive.Files.List request = service.files().list()
                .setQ("mimeType='application/vnd.google-apps.folder' and trashed=false and 'root' in parents");
            FileList files = request.execute();;
            for (File folderfiles : files.getItems()) {
                if (folderfiles.getTitle().equals(name)) {
                    return folderfiles;
                }
            }
            return null;
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
        }
        return null;
    }

    /**
     * Returns a list of files in the specified folder in the authenticated user's Google Drive, ordered by creation date.
     * @param folder the folder containing the files
     * @return a list of files
     * @throws Exception
     */
    @NotNull
    private List<ChildReference> getFiles(@NotNull File folder) throws Exception {
        //Create a List to store results
        List<ChildReference> result = new ArrayList<>();
        //Set up a request to query all files from all pages.
        //We are also making sure the files are sorted by created Date.
        //Oldest at the beginning of List.
        //Drive.Files.List request = service.files().list().setOrderBy("createdDate");
        //folder.getId();
        Drive.Children.List request = service.children().list(folder.getId()).setOrderBy("createdDate");
        //While there is a page available, request files and add them to the Result List.
        do {
            try {
                ChildList files = request.execute();
                result.addAll(files.getItems());
                request.setPageToken(files.getNextPageToken());
            } catch (IOException e) {
                MessageUtil.sendConsoleException(e);
                request.setPageToken(null);
            }
        } while (request.getPageToken() != null &&
                request.getPageToken().length() > 0);
        return result;
    }

    /**
     * Deletes the oldest files in the specified folder past the number to retain from the authenticated user's Google Drive.
     * <p>
     * The number of files to retain is specified by the user in the {@code config.yml}
     * @param folder the folder containing the files
     * @throws Exception
     */
    private void pruneBackups(File folder) throws Exception {
        int fileLimit = ConfigParser.getConfig().backupStorage.keepCount;
        if (fileLimit == -1) {
            return;
        }
        List<ChildReference> files = getFiles(folder);
        if (files.size() > fileLimit) {
            logger.info(
                intl("backup-method-limit-reached"), 
                "file-count", String.valueOf(files.size()),
                "upload-method", getName(),
                "file-limit", String.valueOf(fileLimit));
            for (Iterator<ChildReference> iterator = files.iterator(); iterator.hasNext(); ) {
                if (files.size() == fileLimit) {
                    break;
                }
                ChildReference file = iterator.next();
                Drive.Files.Delete removeItem = service.files().delete(file.getId()).setSupportsAllDrives(true);
                removeItem.execute();
                iterator.remove();
            }
        }
    }
}

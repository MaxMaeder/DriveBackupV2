package ratismal.drivebackup.uploaders.googledrive;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.ChildList;
import com.google.api.services.drive.model.ChildReference;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.spongepowered.configurate.CommentedConfigurationNode;
import ratismal.drivebackup.configuration.ConfigHandler;
import ratismal.drivebackup.http.HttpClient;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.uploaders.AuthenticationProvider;
import ratismal.drivebackup.uploaders.Authenticator;
import ratismal.drivebackup.uploaders.Obfusticate;
import ratismal.drivebackup.uploaders.UploadLogger;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.util.NetUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by Ratismal on 2016-01-20.
 */

public final class GoogleDriveUploader extends Uploader {
    
    private static final String APPLICATION_VND_GOOGLE_APPS_FOLDER = "application/vnd.google-apps.folder";
    private static final String UPLOADER_NAME = "Google Drive";
    private static final String ID = "googledrive";

    /**
     * A global instance of the HTTP transport
     */
    private static final HttpTransport httpTransport = new NetHttpTransport();

    /**
     * A global instance of the JSON factory
     */
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();
    
    private String refreshToken;
    
    /**
     * A cached instance of shared drives
     */
    private List<com.google.api.services.drive.model.Drive> drives;
    /**
     * Global Google Drive API client
     */
    private Drive service;


    /**
     * Creates an instance of the {@code GoogleDriveUploader} object
     */
    public GoogleDriveUploader(DriveBackupInstance instance, UploadLogger logger) {
        super(instance, UPLOADER_NAME,  ID, AuthenticationProvider.GOOGLE_DRIVE, logger);
        try {
            refreshToken = Authenticator.getRefreshToken(AuthenticationProvider.GOOGLE_DRIVE);
            retrieveNewAccessToken();
            drives = service.drives().list().execute().getItems();
        } catch (Exception e) {
            instance.getLoggingHandler().error("Failed to create Google Drive uploader", e);
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
        Response response = HttpClient.getHttpClient().newCall(request).execute();
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

    @Contract(pure = true)
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
    private static HttpRequestInitializer setTimeout(HttpRequestInitializer requestInitializer) {
        return httpRequest -> {
            requestInitializer.initialize(httpRequest);
            // 1 minute connect timeout
            httpRequest.setConnectTimeout((int) TimeUnit.MINUTES.toMillis(1L));
            // 4-hour read timeout
            httpRequest.setReadTimeout((int) TimeUnit.HOURS.toMillis(4L));
        };
    }

    /**
     * Tests the Google Drive account by uploading a small file
     * @param testFile the file to upload during the test
     */
    public void test(java.io.File testFile) {
        try {
            String sharedDriveId = instance.getConfigHandler().getConfig().getValue("googledrive", "shared-drive-id").getString();
            String destination = getRemoteSaveDirectory();
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
            TimeUnit.SECONDS.sleep(5L);
            service.files().delete(fileId).setSupportsAllDrives(true).execute();
        } catch (Exception exception) {
            NetUtil.catchException(exception, "www.googleapis.com", logger);
            instance.getLoggingHandler().error("Failed to test Google Drive", exception);
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
            String sharedDriveId = instance.getConfigHandler().getConfig().getValue("googledrive", "shared-drive-id").getString();
            String destination = getRemoteSaveDirectory();
            retrieveNewAccessToken();
            Collection<String> typeFolders = new ArrayList<>();
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
                    logger.log("backup-method-shared-drive-prune-failed");
                } else {
                    logger.log("backup-method-prune-failed");
                }
                throw e;
            }
        } catch (Exception exception) {
            NetUtil.catchException(exception, "www.googleapis.com", logger);
            instance.getLoggingHandler().error("Failed to upload file to Google Drive", exception);
            setErrorOccurred(true);
        }
    }

    /**
     * Closes any remaining connections retrieveNewAccessToken
     */
    @Contract (pure = true)
    public void close() {
        // nothing needs to be done
    }

    /**
     * Setup for authenticated user that has access to one or more shared drives.
     */
    public void setupSharedDrives(CommandSender initiator) {
        if (drives != null && !drives.isEmpty()) {
            logger.log("google-pick-shared-drive");
            String msg = instance.getMessageHandler().getLangString("default-google-drive-name");
            Map<String, String> placeholders = new HashMap<>(3);
            placeholders.put("select-command", "1");
            placeholders.put("drive-num", "1");
            placeholders.put("drive-name", msg);
            logger.log("google-shared-drive-option", placeholders);
            int index = 1;
            for (com.google.api.services.drive.model.Drive drive : drives) {
                Map<String, String> placeholders2 = new HashMap<>(3);
                placeholders2.put("select-command", String.valueOf(index + 1));
                placeholders2.put("drive-num", String.valueOf(index + 1));
                placeholders2.put("drive-name", drive.getName());
                logger.log("google-shared-drive-option", placeholders2);
            }
            //TODO
            if (initiator instanceof Player) {
                Player player = (Player) initiator;
                DriveBackup.chatInputPlayers.add(player);
            } else {
                DriveBackup.chatInputPlayers.add(initiator);
            }
        } else {
            Authenticator.linkSuccess(initiator, getAuthProvider(), logger, instance);
        }
    }

    public void finalizeSharedDrives(CommandSender initiator, String input) {
        final String idKey = "googledrive.shared-drive-id";
        ConfigHandler configHandler = instance.getConfigHandler();
        CommentedConfigurationNode config = configHandler.getConfig().getConfig();
        try {
            for (com.google.api.services.drive.model.Drive drive : drives) {
                if (input.equals(drive.getId())) {
                    config.node(idKey).set(input);
                    configHandler.save();
                    Authenticator.linkSuccess(initiator, getAuthProvider(), logger, instance);
                    return;
                }
            }
            if ("1".equals(input)) {
                config.node(idKey).set("");
                configHandler.save();
                Authenticator.linkSuccess(initiator, getAuthProvider(), logger, instance);
                return;
            } else if (input.matches("[0-9]+")) {
                config.node(idKey).set(drives.get(Integer.parseInt(input) - 2).getId());
                configHandler.save();
                Authenticator.linkSuccess(initiator, getAuthProvider(), logger, instance);
                return;
            }
        } catch (Exception e) {
            instance.getLoggingHandler().error("Failed to finalize shared drive setup", e);
        }
        // TODO: handle this better
        logger.log("link-provider-failed", "provider", getAuthProvider().getName());
    }

    /**
     * Creates a folder with the specified name in the specified parent folder in the authenticated user's Google Drive.
     * @param name the name of the folder
     * @param parent the parent folder
     * @return the created folder
     * @throws Exception
     */
    private File createFolder(String name, File parent, boolean sharedDrive) throws Exception {
        File folder;
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
        folder = service.files().insert(folder).setSupportsAllDrives(Boolean.TRUE).execute();
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
        File folder;
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
        folder = service.files().insert(folder).setSupportsAllDrives(Boolean.TRUE).execute();
        return folder;
    }

    /**
     * Creates a folder with the specified name in the root of the authenticated user's Google Drive.
     * @param name the name of the folder
     * @return the created folder
     * @throws Exception
     */
    private File createFolder(String name) throws Exception {
        File folder;
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
                .setSupportsAllDrives(Boolean.TRUE)
                .setIncludeItemsFromAllDrives(Boolean.TRUE)
                .setCorpora("drive")
                .setQ("mimeType='application/vnd.google-apps.folder' and trashed=false and '" + driveId + "' in parents");
            FileList files = request.execute();
            for (File folderFiles : files.getItems()) {
                if (folderFiles.getTitle().equals(name)) {
                    return folderFiles;
                }
            }
        } catch (Exception e) {
            instance.getLoggingHandler().error("Failed to get folder", e);
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
                request.setSupportsAllDrives(Boolean.TRUE)
                .setIncludeItemsFromAllDrives(Boolean.TRUE)
                .setCorpora("allDrives");
            }
            FileList files = request.execute();
            for (File folderFiles : files.getItems()) {
                if (folderFiles.getTitle().equals(name)) {
                    return folderFiles;
                }
            }
        } catch (Exception e) {
            instance.getLoggingHandler().error("Failed to get folder", e);
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
            FileList files = request.execute();
            for (File folderFiles : files.getItems()) {
                if (folderFiles.getTitle().equals(name)) {
                    return folderFiles;
                }
            }
        } catch (Exception e) {
            instance.getLoggingHandler().error("Failed to get folder", e);
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
                instance.getLoggingHandler().error("Failed to get files", e);
                request.setPageToken(null);
            }
        } while (request.getPageToken() != null && !request.getPageToken().isEmpty());
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
        int fileLimit = getKeepCount();
        if (fileLimit == -1) {
            return;
        }
        List<ChildReference> files = getFiles(folder);
        if (files.size() > fileLimit) {
            Map<String, String> placeholders = new HashMap<>(3);
            placeholders.put("file-count", String.valueOf(files.size()));
            placeholders.put("upload-method", getName());
            placeholders.put("file-limit", String.valueOf(fileLimit));
            logger.info("backup-method-limit-reached", placeholders);
            for (Iterator<ChildReference> iterator = files.iterator(); iterator.hasNext(); ) {
                if (files.size() == fileLimit) {
                    break;
                }
                ChildReference file = iterator.next();
                Drive.Files.Delete removeItem = service.files().delete(file.getId()).setSupportsAllDrives(Boolean.TRUE);
                removeItem.execute();
                iterator.remove();
            }
        }
    }
}

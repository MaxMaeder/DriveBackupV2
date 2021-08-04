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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import main.java.credentials.GoogleDriveCredentials;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.uploaders.Authenticator;
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
import org.bukkit.conversations.*;
import org.json.JSONObject;

import static ratismal.drivebackup.config.Localization.intl;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class GoogleDriveUploader implements Uploader {
    private UploadLogger logger;
    private boolean errorOccurred;
    private String refreshToken;

    public static final String UPLOADER_NAME = "Google Drive";

    /**
     * Global instance of the HTTP client
     */
    private static final OkHttpClient httpClient = new OkHttpClient();

    /**
     * Global instance of the HTTP transport
     */
    private static final HttpTransport httpTransport = new NetHttpTransport();

    /**
     * Global instance of the JSON factory
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
        this.logger = logger;

        try {
            refreshToken = Authenticator.getRefreshToken(AuthenticationProvider.GOOGLE_DRIVE);
            retrieveNewAccessToken();
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Gets a new Google Drive access token for the authenticated user
     */
    private void retrieveNewAccessToken() throws Exception {
        RequestBody requestBody = new FormBody.Builder()
            .add("client_id", GoogleDriveCredentials.CLIENT_ID)
            .add("client_secret", GoogleDriveCredentials.CLIENT_SECRET)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build();

        Request request = new Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(requestBody)
            .build();

        Response response = httpClient.newCall(request).execute();
        JSONObject parsedResponse = new JSONObject(response.body().string());
        response.close();
        
        if (!response.isSuccessful()) return;

        service = new Drive.Builder(
            httpTransport, 
            JSON_FACTORY, 
            setTimeout(new Credential(
                BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(parsedResponse.getString("access_token"))))
            .setApplicationName("DriveBackupV2")
            .build();
    }

    /**
     * Sets the connect/read timeouts of the Google Drive Client by implementing {@code HttpRequestInitializer}
     * @param requestInitializer the default {@code HttpRequestInitializer} provided by the Google Drive Client
     * @return the modified {@code HttpRequestInitializer} with the connect/read timeouts set 
     */
    private HttpRequestInitializer setTimeout(final HttpRequestInitializer requestInitializer) {
        return new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest httpRequest) throws IOException {
                requestInitializer.initialize(httpRequest);
                httpRequest.setConnectTimeout(1 * 60000); // 1 minute connect timeout
                httpRequest.setReadTimeout(4 * 60 * 60000); // 4 hours read timeout
            }
        };
    }

    /**
     * Tests the Google Drive account by uploading a small file
     * @param testFile the file to upload during the test
     */
    public void test(java.io.File testFile) {
        try {
            String destination = ConfigParser.getConfig().backupStorage.remoteDirectory;
            File body = new File();
                body.setTitle(testFile.getName());
                body.setDescription("DriveBackupV2 test file");

            FileContent mediaContent = new FileContent("plain/txt", testFile);

            File folder = getFolder(destination);
            ParentReference fileParent = new ParentReference();
            fileParent.setId(folder.getId());
            body.setParents(Collections.singletonList(fileParent));

            File uploadedFile = service.files().insert(body, mediaContent).execute();
            String fileId = uploadedFile.getId();
            
            TimeUnit.SECONDS.sleep(5);
                
            service.files().delete(fileId).execute();
        } catch (Exception exception) {
            NetUtil.catchException(exception, "www.googleapis.com", logger);
            MessageUtil.sendConsoleException(exception);
            setErrorOccurred(true);
        }
    }

    /**
     * Uploads the specified file to the authenticated user's Google Drive inside a folder for the specified file type
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     */
    public void uploadFile(java.io.File file, String type) {
        try {
            String sharedDriveId = ConfigParser.getConfig().backupMethods.googleDrive.sharedDriveId;
            String destination = ConfigParser.getConfig().backupStorage.remoteDirectory;

            ArrayList<String> typeFolders = new ArrayList<>();
            Collections.addAll(typeFolders, destination.split("/"));
            Collections.addAll(typeFolders, type.split("/"));

            File folder = null;

            for (String typeFolder : typeFolders) {
                if (typeFolder.equals(".") || typeFolder.equals("..")) {
                    continue;
                }

                if (folder == null && !sharedDriveId.isEmpty()) {
                    folder = createFolder(typeFolder, sharedDriveId);
                } else if (folder == null) {
                    folder = createFolder(typeFolder);
                } else {
                    folder = createFolder(typeFolder, folder);
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

            pruneBackups(folder);
        } catch (Exception exception) {
            NetUtil.catchException(exception, "www.googleapis.com", logger);
            MessageUtil.sendConsoleException(exception);
            setErrorOccurred(true);
        }
    }

    /**
     * Gets whether an error occurred while accessing the authenticated user's Google Drive
     * @return whether an error occurred
     */
    public boolean isErrorWhileUploading() {
        return this.errorOccurred;
    }

    /**
     * closes any remaining connectionsretrieveNewAccessToken
     */
    public void close() {
        return; // nothing needs to be done
    }

    /**
     * Gets the name of this upload service
     * @return name of upload service
     */
    public String getName()
    {
        return UPLOADER_NAME;
    }

    public AuthenticationProvider getAuthProvider() {
        return AuthenticationProvider.GOOGLE_DRIVE;
    }

    /**
     * Setup for authenticated user that has access to one or more shared drives.
     * @throws Exception
     */
    public boolean setupSharedDrives(CommandSender initiator) throws Exception {
        AuthenticationProvider provider = AuthenticationProvider.GOOGLE_DRIVE;
        List<com.google.api.services.drive.model.Drive> drives = service.drives().list().execute().getItems();

        if (drives.size() > 0) {
            logger.log(intl("google-pick-shared-drive"));

            logger.log(
                intl("google-shared-drive-option"),
                "select-command", "1",
                "drive-num", "1",
                "drive-name", "My Drive"); 

            int index = 2;
            for (com.google.api.services.drive.model.Drive drive : drives) {
                logger.log(
                    intl("google-shared-drive-option"),
                    "select-command", drive.getId(),
                    "drive-num", String.valueOf(index++),
                    "drive-name", drive.getName()); 
            }
            final Prompt driveId = new StringPrompt() {

                @Override
                public String getPromptText(final ConversationContext context) {
                    return "";
                }
    
                @Override
                public Prompt acceptInput(final ConversationContext context, String input) {
                    final DriveBackup instance = DriveBackup.getInstance();
                    final String idKey = "googledrive.shared-drive-id";

                    for (com.google.api.services.drive.model.Drive drive : drives) {
                        if (input.equals(drive.getId())) {

                            instance.getConfig().set(idKey, input);
                            instance.saveConfig();
                            Authenticator.linkSuccess(initiator, provider, logger);

                            return Prompt.END_OF_CONVERSATION;
                        }
                    }

                    logger.log("got: " + input);

                    if (input.equals("1")) {

                        instance.getConfig().set(idKey, "");
                        instance.saveConfig();
                        Authenticator.linkSuccess(initiator, provider, logger);

                        return Prompt.END_OF_CONVERSATION;
                    } else if (input.matches("[0-9]+")) {
                        logger.log("in here");

                        instance.getConfig().set(idKey, drives.get(Integer.parseInt(input) - 2).getId());
                        instance.saveConfig();
                        Authenticator.linkSuccess(initiator, provider, logger);
                        
                        return Prompt.END_OF_CONVERSATION;
                    }

                    // TODO: handle this better
                    Authenticator.linkFail(provider, logger);
                    return Prompt.END_OF_CONVERSATION;
                }
            };
    
            final ConversationFactory factory = new ConversationFactory(DriveBackup.getInstance())
                .withTimeout(60)
                .withLocalEcho(false)
                .withFirstPrompt(driveId);
    
            factory.buildConversation((Conversable) initiator).begin();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Creates a folder with the specified name in the specified parent folder in the authenticated user's Google Drive
     * @param name the name of the folder
     * @param parent the parent folder
     * @return the created folder
     * @throws Exception
     */
    private File createFolder(String name, File parent) throws Exception {
        File folder = null;

        folder = getFolder(name, parent);
        if (folder != null) {
            return folder;
        }

        ParentReference parentReference = new ParentReference();
        parentReference.setId(parent.getId());

        folder = new File();
        folder.setTitle(name);
        folder.setMimeType("application/vnd.google-apps.folder");
        folder.setParents(Collections.singletonList(parentReference));

        folder = service.files().insert(folder).setSupportsAllDrives(true).execute();

        return folder;
    }

    /**
     * Creates a folder with the specified name in the specified parent folder in the authenticated user's specified Shared Drive
     * @param name the name of the folder
     * @param parent the parent folder
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
        folder.setMimeType("application/vnd.google-apps.folder");
        folder.setParents(Collections.singletonList(parentReference));

        folder = service.files().insert(folder).setSupportsAllDrives(true).execute();

        return folder;
    }

    /**
     * Creates a folder with the specified name in the root of the authenticated user's Google Drive
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
        folder.setMimeType("application/vnd.google-apps.folder");

        folder = service.files().insert(folder).execute();

        return folder;
    }

    /**
     * Returns the folder in the specified parent folder of the authenticated user's Google Drive with the specified name
     * @param name the name of the folder
     * @param parent the parent folder
     * @return the folder or {@code null}
     */
    private File getFolder(String name, String driveId) {
        try {
            Drive.Files.List request = service.files().list()
                .setDriveId(driveId)
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .setCorpora("drive")
                .setQ("mimeType='application/vnd.google-apps.folder' and trashed=false");
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
     * Returns the folder in the specified parent folder of the authenticated user's Google Drive with the specified name
     * @param name the name of the folder
     * @param parent the parent folder
     * @return the folder or {@code null}
     */
    private File getFolder(String name, File parent) {
        try {
            Drive.Files.List request = service.files().list()
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .setQ("mimeType='application/vnd.google-apps.folder' and trashed=false and '" + parent.getId() + "' in parents");
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
     * Returns the folder in the root of the authenticated user's Google Drive with the specified name
     * @param name the name of the folder
     * @return the folder or {@code null}
     */
    private File getFolder(String name) {
        try {
            Drive.Files.List request = service.files().list()
                .setSupportsAllDrives(true)
                .setQ("mimeType='application/vnd.google-apps.folder' and trashed=false");
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
     * Returns a list of files in the specified folder in the authenticated user's Google Drive, ordered by creation date
     * @param folder the folder containing the files
     * @return a list of files
     * @throws Exception
     */
    private List<ChildReference> getFiles(File folder) throws Exception {

        //Create a List to store results
        List<ChildReference> result = new ArrayList<>();

        //Set up a request to query all files from all pages.
        //We are also making sure the files are sorted  by created Date. Oldest at the beginning of List.
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
     * Deletes the oldest files in the specified folder past the number to retain from the authenticated user's Google Drive
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
                Drive.Files.Delete removeItem = service.files().delete(file.getId());
                removeItem.execute();
                iterator.remove();
            }
        }
    }

    /**
     * Sets whether an error occurred while accessing the authenticated user's Google Drive
     * @param errorOccurredValue whether an error occurred
     */
    private void setErrorOccurred(boolean errorOccurredValue) {
        this.errorOccurred = errorOccurredValue;
    }
}
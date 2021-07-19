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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.handler.commandHandler.BasicCommands;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.SchedulerUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.json.JSONObject;

import static ratismal.drivebackup.config.Localization.intl;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class GoogleDriveUploader implements Uploader {
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
     * Location of the authenticated user's stored Google Drive refresh token
     */
    private static final String CLIENT_JSON_PATH = DriveBackup.getInstance().getDataFolder().getAbsolutePath()
        + "/GoogleDriveCredential.json";
    
    /**
     * Google Drive API credentials
     */
    private static final String CLIENT_ID = "***REMOVED***";
    private static final String CLIENT_SECRET = "***REMOVED***";

    /**
     * Global Google Drive API client
     */
    private Drive service;

    /**
     * Attempt to authenticate a user with Google Drive using the OAuth 2.0 device
     * authorization grant flow
     * 
     * @param plugin    a reference to the {@code DriveBackup} plugin
     * @param initiator user who initiated the authentication
     */
    public static void authenticateUser(final DriveBackup plugin, final CommandSender initiator) {
        try {
            RequestBody requestBody = new FormBody.Builder()
                .add("type", "googledrive")
                .build();

            Request request = new Request.Builder()
                .url("https://drivebackup.web.app/pin")
                .post(requestBody)
                .build();

            Response response = httpClient.newCall(request).execute();
            JSONObject parsedResponse = new JSONObject(response.body().string());
            response.close();

            String verificationUrl = "https://drivebackup.web.app/";
            String userCode = parsedResponse.getString("user_code");
            final String deviceCode = parsedResponse.getString("device_code");
            long responseCheckDelay = SchedulerUtil.sToTicks(parsedResponse.getLong("interval"));

            MessageUtil.Builder()
                .mmText(
                    intl("link-account-code")
                        .replace("link-url", verificationUrl)
                        .replace("link-code", userCode)
                        .replace("provider", UPLOADER_NAME)
                    )
                .to(initiator)
                .toConsole(false)
                .send();

            final int[] task = new int[]{-1};
            task[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
                @Override
                public void run() {
                    
                    RequestBody requestBody = new FormBody.Builder()
                        .add("device_code", deviceCode)
                        .add("user_code", userCode)
                        .build();
            
                    Request request = new Request.Builder()
                        .url("https://drivebackup.web.app/token")
                        .post(requestBody)
                        .build();
            
                    JSONObject parsedResponse = null;
                    try {
                        Response response = httpClient.newCall(request).execute();
                        parsedResponse = new JSONObject(response.body().string());
                        response.close();
                    } catch (Exception exception) {
                        MessageUtil.Builder().text("Failed to link your Google Drive account, please try again").to(initiator).toConsole(false).send();
                        MessageUtil.sendConsoleException(exception);

                        Bukkit.getScheduler().cancelTask(task[0]);
                        return;
                    }
                    
                    if (parsedResponse.has("refresh_token")) {
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("refresh_token", parsedResponse.getString("refresh_token"));

                        try {
                            FileWriter file = new FileWriter(CLIENT_JSON_PATH);
                            file.write(jsonObject.toString());
                            file.close();
                        } catch (IOException e) {
                            MessageUtil.Builder().text("Failed to link your Google Drive account, please try again").to(initiator).toConsole(false).send();
                            MessageUtil.sendConsoleException(e);
                            
                            Bukkit.getScheduler().cancelTask(task[0]);
                        }
                        
                        MessageUtil.Builder().text("Your Google Drive account is linked!").to(initiator).toConsole(false).send();
                        
                        if (!plugin.getConfig().getBoolean("googledrive.enabled")) {
                            MessageUtil.Builder().text("Automatically enabled Google Drive backups").to(initiator).toConsole(false).send();
                            plugin.getConfig().set("googledrive.enabled", true);
                            plugin.saveConfig();
                            
                            DriveBackup.reloadLocalConfig();
                        }

                        BasicCommands.sendBriefBackupList(initiator);
                        
                        Bukkit.getScheduler().cancelTask(task[0]);
                    } else if (!parsedResponse.getString("msg").equals("Code not authenticated")) {
                        if (parsedResponse.getString("msg").equals("code_expired")) {
                            MessageUtil.Builder().text("The Google Drive account linking process timed out, please try again").to(initiator).toConsole(false).send();
                        } else {
                            MessageUtil.Builder().text("Failed to link your Google Drive account, please try again" + parsedResponse.toString()).to(initiator).toConsole(false).send();
                        }
                        
                        Bukkit.getScheduler().cancelTask(task[0]);
                    }
                }
            }, responseCheckDelay, responseCheckDelay);
        } catch (UnknownHostException exception) {
            MessageUtil.Builder().text("Failed to link your Google Drive account, check your network connection").toPerm("drivebackup.linkAccounts").send();
            
        } catch (Exception e) {
            MessageUtil.Builder().text("Failed to link your Google Drive account").to(initiator).toConsole(false).send();
        
            MessageUtil.sendConsoleException(e);
        }
    }

    /**
     * Creates an instance of the {@code GoogleDriveUploader} object
     */
    public GoogleDriveUploader() {
        try {
            setRefreshTokenFromStoredValue();
            retrieveNewAccessToken();
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Sets the authenticated user's stored Google Drive refresh token from the stored value
     * @throws Exception
     */
    private void setRefreshTokenFromStoredValue() throws Exception {
        String clientJSON = processCredentialJsonFile();
        JSONObject clientJsonObject = new JSONObject(clientJSON);

        String readRefreshToken = (String) clientJsonObject.get("refresh_token");

        if (readRefreshToken != null && !readRefreshToken.isEmpty()) {
            setRefreshToken(readRefreshToken);
        } else {
            setRefreshToken("");
        }   
    }


    /**
     * Gets a new Google Drive access token for the authenticated user
     */
    private void retrieveNewAccessToken() throws Exception {
        RequestBody requestBody = new FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("refresh_token", returnRefreshToken())
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
        } catch (UnknownHostException exception) {
            MessageUtil.Builder().text("Failed to upload test file to Google Drive, check your network connection").toPerm("drivebackup.linkAccounts").send();
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
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
            String destination = ConfigParser.getConfig().backupStorage.remoteDirectory;

            ArrayList<String> typeFolders = new ArrayList<>();
            Collections.addAll(typeFolders, destination.split("/"));
            Collections.addAll(typeFolders, type.split("/"));
            
            File folder = null;

            for (String typeFolder : typeFolders) {
                if (typeFolder.equals(".") || typeFolder.equals("..")) {
                    continue;
                }

                try {
                    if (folder == null) {
                        folder = createFolder(typeFolder);
                    } else {
                        folder = createFolder(typeFolder, folder);
                    }
                } catch (Exception exception) {
                    MessageUtil.Builder().text("Failed to create folder(s) in Google Drive, these folders MUST NOT exist before the plugin creates them.").toConsole(true).send();

                    throw exception;
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

            service.files().insert(fileMetadata, fileContent).execute();

            deleteFiles(folder);
        } catch (UnknownHostException exception) {
            MessageUtil.Builder().text("Failed to upload backup to Google Drive, check your network connection").toPerm("drivebackup.linkAccounts").send();
            setErrorOccurred(true);
        } catch(Exception error) {
            MessageUtil.sendConsoleException(error);
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

    /**
     * Gets the setup instructions for this uploaders
     * @return a Component explaining how to set up this uploader
     */
    public TextComponent getSetupInstructions()
    {
        return Component.text()
                    .append(
                        Component.text("Failed to backup to Google Drive, please run ")
                        .color(NamedTextColor.DARK_AQUA)
                    )
                    .append(
                        Component.text("/drivebackup linkaccount googledrive")
                        .color(NamedTextColor.GOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("Run command")))
                        .clickEvent(ClickEvent.runCommand("/drivebackup linkaccount googledrive"))
                    )
                    .build();
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

        folder = service.files().insert(folder).execute();

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
    private File getFolder(String name, File parent) {
        try {
            Drive.Files.List request = service.files().list().setQ(
                    "mimeType='application/vnd.google-apps.folder' and trashed=false and '" + parent.getId() + "' in parents");
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
            Drive.Files.List request = service.files().list().setQ(
                    "mimeType='application/vnd.google-apps.folder' and trashed=false");
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
    private void deleteFiles(File folder) throws Exception {
        int fileLimit = ConfigParser.getConfig().backupStorage.keepCount;

        if (fileLimit == -1) {
            return;
        }

        List<ChildReference> queriedFilesfromDrive = getFiles(folder);
        if (queriedFilesfromDrive.size() > fileLimit) {
            MessageUtil.Builder().text("There are " + queriedFilesfromDrive.size() + " file(s) which exceeds the limit of " + fileLimit + ", deleting").toConsole(true).send();

            for (Iterator<ChildReference> iterator = queriedFilesfromDrive.iterator(); iterator.hasNext(); ) {
                if (queriedFilesfromDrive.size() == fileLimit) {
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
     * Gets the authenticated user's stored Google Drive credentials
     * <p>
     * The refresh token is stored in {@code /GoogleDriveCredential.json}
     * @return the credentials as a {@code String}
     * @throws IOException
     */
    private static String processCredentialJsonFile() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(CLIENT_JSON_PATH));
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        while (line != null) {
            sb.append(line);
            line = br.readLine();
        }
        
        String result = sb.toString();
        br.close(); 

        return result;
    }

    /**
     * Sets whether an error occurred while accessing the authenticated user's Google Drive
     * @param errorOccurredValue whether an error occurred
     */
    private void setErrorOccurred(boolean errorOccurredValue) {
        this.errorOccurred = errorOccurredValue;
    }

    /**
     * Sets the refresh token of the authenticated user
     * @param refreshTokenValue the refresh token
     */
    private void setRefreshToken(String refreshTokenValue) {
        this.refreshToken = refreshTokenValue;
    }

    /**
     * Gets the refresh token of the authenticated user
     * @return the refresh token
     */
    private String returnRefreshToken() {
        return this.refreshToken;
    }
}
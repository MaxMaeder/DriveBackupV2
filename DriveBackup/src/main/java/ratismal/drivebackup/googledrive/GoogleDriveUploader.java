package ratismal.drivebackup.googledrive;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.*;
import net.kyori.text.TextComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.util.MessageUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitScheduler;
import org.json.JSONObject;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class GoogleDriveUploader {
    private boolean errorOccurred;
    private String refreshToken;

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
    private static final String CLIENT_ID = "602937851350-5ftqapmmpahc0476h7ng5tjeisivqqle.apps.googleusercontent.com";
    private static final String CLIENT_SECRET = "6KEvP9LNfWFuFdTMPSdjwUeg";

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
     * @throws Exception
     */
    public static void authenticateUser(final DriveBackup plugin, final CommandSender initiator) throws Exception {
        RequestBody requestBody = new FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", "https://www.googleapis.com/auth/drive.file")
            .build();

        Request request = new Request.Builder()
            .url("https://oauth2.googleapis.com/device/code")
            .post(requestBody)
            .build();

        Response response = httpClient.newCall(request).execute();
        JSONObject parsedResponse = new JSONObject(response.body().string());
        response.close();

        String verificationUrl = parsedResponse.getString("verification_url");
        String userCode = parsedResponse.getString("user_code");
        final String deviceCode = parsedResponse.getString("device_code");
        long responseCheckDelay = parsedResponse.getLong("interval");

        MessageUtil.sendMessage(initiator, TextComponent.builder()
                .append(
                    TextComponent.of("To link your Google Drive account, go to ")
                    .color(TextColor.DARK_AQUA)
                )
                .append(
                    TextComponent.of(verificationUrl)
                    .color(TextColor.GOLD)
                    .hoverEvent(HoverEvent.showText(TextComponent.of("Go to URL")))
                    .clickEvent(ClickEvent.openUrl(verificationUrl))
                )
                .append(
                    TextComponent.of(" and enter code ")
                    .color(TextColor.DARK_AQUA)
                )
                .append(
                    TextComponent.of(userCode)
                    .color(TextColor.GOLD)
                    .hoverEvent(HoverEvent.showText(TextComponent.of("Copy code")))
                    .clickEvent(ClickEvent.copyToClipboard(userCode))
                )
                .build());

        final int[] task = new int[]{-1};
        task[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                
                RequestBody requestBody = new FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .add("device_code", deviceCode)
                    .build();
        
                Request request = new Request.Builder()
                    .url("https://oauth2.googleapis.com/token")
                    .post(requestBody)
                    .build();
        
                JSONObject parsedResponse = null;
                try {
                    Response response = httpClient.newCall(request).execute();
                    parsedResponse = new JSONObject(response.body().string());
                    response.close();
                } catch (Exception exception) {
                    MessageUtil.sendMessage(initiator, "Failed to link your Google Drive account, please try again");

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
                        MessageUtil.sendMessage(initiator, "Failed to link your Google Drive account, please try again");
                        
                        Bukkit.getScheduler().cancelTask(task[0]);
                    }
                    
                    MessageUtil.sendMessage(initiator, "Your Google Drive account is linked!");
                    
                    MessageUtil.sendMessage(initiator, "Automatically enabled Google Drive backups");
                    plugin.getConfig().set("googledrive.enabled", true);
                    plugin.saveConfig();
                    
                    DriveBackup.reloadLocalConfig();
                    BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
                    scheduler.cancelTasks(DriveBackup.getInstance());
                    DriveBackup.startThread();
                    
                    Bukkit.getScheduler().cancelTask(task[0]);
                } else if (!parsedResponse.getString("error").equals("authorization_pending")) {
                    if (parsedResponse.getString("error").equals("expired_token")) {
                        MessageUtil.sendMessage(initiator, "The Google Drive account linking process timed out, please try again");
                    } else {
                        MessageUtil.sendMessage(initiator, "Failed to link your Google Drive account, please try again");
                    }
                    
                    Bukkit.getScheduler().cancelTask(task[0]);
                }
            }
        }, responseCheckDelay * 20L, responseCheckDelay * 20L);
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
            new Credential(
                BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(parsedResponse.getString("access_token")))
            .setApplicationName("DriveBackupV2")
            .build();
    }

    /**
     * Uploads the specified file to the authenticated user's Google Drive inside a folder for the specified file type
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     */
    public void uploadFile(java.io.File file, String type) {
        try {
            String destination = Config.getDestination();

            ArrayList<String> typeFolders = new ArrayList<>();
            Collections.addAll(typeFolders, destination.split(java.io.File.separator.replace("\\", "\\\\")));
            Collections.addAll(typeFolders, type.split(java.io.File.separator.replace("\\", "\\\\")));
            
            File folder = null;

            for (String typeFolder : typeFolders) {
                if (typeFolder.equals(".") || typeFolder.equals("..")) {
                    continue;
                }

                if (folder == null) {
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

            service.files().insert(fileMetadata, fileContent).execute();

            deleteFiles(folder);
        } catch(Exception error) {;
            error.printStackTrace();
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
        int fileLimit = Config.getKeepCount();

        if (fileLimit == -1) {
            return;
        }

        List<ChildReference> queriedFilesfromDrive = getFiles(folder);
        if (queriedFilesfromDrive.size() > fileLimit) {
            MessageUtil.sendConsoleMessage("There are " + queriedFilesfromDrive.size() + " file(s) which exceeds the limit of " + fileLimit + ", deleting");

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

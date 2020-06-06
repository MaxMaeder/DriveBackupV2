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
import io.restassured.response.Response;
import net.kyori.text.TextComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.util.MessageUtil;

import static io.restassured.RestAssured.given;

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
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class GoogleDriveUploader {
    private boolean errorOccurred;
    private String refreshToken;

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
     * Attempt to authenticate a user with Google Drive using the OAuth 2.0 device authorization grant flow
     * @param plugin a reference to the {@code DriveBackup} plugin
     * @param initiator user who initiated the authentication
     * @throws Exception
     */
    public static void authenticateUser(final DriveBackup plugin, final CommandSender initiator) throws Exception {
        Response response = given()
      		.contentType("application/x-www-form-urlencoded")
      		.param("client_id", CLIENT_ID)
      		.param("scope", "https://www.googleapis.com/auth/drive.file")
      		.post("https://oauth2.googleapis.com/device/code");

        String verificationUrl = response.getBody().jsonPath().getString("verification_url");
        String userCode = response.getBody().jsonPath().getString("user_code");
        final String deviceCode = response.getBody().jsonPath().getString("device_code");
        long responseCheckDelay = response.getBody().jsonPath().getLong("interval");

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
                
                Response response = given()
                    .contentType("application/x-www-form-urlencoded")
                    .param("client_id", CLIENT_ID)
                    .param("client_secret", CLIENT_SECRET)
                    .param("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .param("device_code", deviceCode)
                    .post("https://oauth2.googleapis.com/token");
                
                if (response.getStatusCode() == 200) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("refresh_token", response.getBody().jsonPath().getString("refresh_token"));

                    try {
                        FileWriter file = new FileWriter(CLIENT_JSON_PATH);
                        file.write(jsonObject.toString());
                        file.close();
                    } catch (IOException e) {
                        MessageUtil.sendMessage(initiator, "Failed to link your Google Drive account");
                        
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
                } else if (!response.getBody().jsonPath().getString("error").equals("authorization_pending")) {
                    MessageUtil.sendMessage(initiator, "Failed to link your Google Drive account");
                    
                    Bukkit.getScheduler().cancelTask(task[0]);
                }
            }
        }, responseCheckDelay * 20L, responseCheckDelay * 20L);
    }

    /**
     * Creates an instance of the {@code GoogleDriveUploader} object
     */
    public GoogleDriveUploader() {
        if (!Config.isGoogleEnabled()) {
            return;
        }

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
        JSONParser jsonParser = new JSONParser();
        JSONObject clientJsonObject = (JSONObject) jsonParser.parse(clientJSON);

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
    private void retrieveNewAccessToken() {
        Response response = given()
      		.contentType("application/x-www-form-urlencoded")
      		.param("client_id", CLIENT_ID)
      		.param("client_secret", CLIENT_SECRET)
      		.param("refresh_token", returnRefreshToken())
      		.param("grant_type", "refresh_token")
            .post("https://oauth2.googleapis.com/token");
        
        if (response.statusCode() != 200) return;

        service = new Drive.Builder(
            httpTransport, 
            JSON_FACTORY, 
            new Credential(
                BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(response.getBody().jsonPath().getString("access_token")))
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
            File body = new File();
            body.setTitle(file.getName());
            body.setDescription("DriveBackup plugin");
            body.setMimeType("application/zip");

            String destination = Config.getDestination();

            FileContent mediaContent = new FileContent("application/zip", file);

            File parentFolder = getFolder(destination);
            if (parentFolder == null) {
                parentFolder = new File();
                parentFolder.setTitle(destination);
                parentFolder.setMimeType("application/vnd.google-apps.folder");
                parentFolder = service.files().insert(parentFolder).execute();
            }

            String[] typeFolders = type.split(java.io.File.separator.replace("\\", "\\\\"));
            
            File childFolder = null;
            ParentReference childFolderParent = new ParentReference();
            
            for (String folder : typeFolders) {
                if (folder.equals(".") || folder.equals("..")) {
                    continue;
                }

                /*if (folder == "..") {
                    if (childFolder == null) {
                        childFolder = service.files().get(parentFolder.getParents().get(0).getId()).execute();
                    } else {
                        childFolder = service.files().get(childFolder.getParents().get(0).getId()).execute();
                    }
                    
                    continue;
                }*/

                if (childFolder == null) {
                    childFolder = getFolder(folder, parentFolder);
                    childFolderParent.setId(parentFolder.getId());
                } else {
                    String ParentFolderId = childFolder.getId();
                    childFolder = getFolder(folder, childFolder);
                    childFolderParent.setId(ParentFolderId);
                }

                if (childFolder == null) {
                    childFolder = new File();
                    childFolder.setTitle(folder);
                    childFolder.setMimeType("application/vnd.google-apps.folder");
                    childFolder.setParents(Collections.singletonList(childFolderParent));
    
                    childFolder = service.files().insert(childFolder).execute();
                }
            }


            ParentReference newParent = new ParentReference();
            newParent.setId(childFolder.getId());
            body.setParents(Collections.singletonList(newParent));

            service.files().insert(body, mediaContent).execute();

            deleteFiles(childFolder);
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
     * Returns a reference to the file in the specified parent folder of the authenticated user's Google Drive with the specified name
     * @param name the name of the file
     * @param parent a reference to the parent folder
     * @return the reference to the file or {@code null}
     */
    private File getFile(String name, File parent) {
        try {
            Drive.Files.List request = service.files().list().setQ(
                    "mimeType='application/zip' and trashed=false and '" + parent.getId() + "' in parents");
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
     * Returns a reference to the file in the root of the authenticated user's Google Drive with the specified name
     * @param name the name of the file
     * @return the reference to the file or {@code null}
     */
    private File getFile(String name) {
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
     * Returns a reference to the folder in the specified parent folder of the authenticated user's Google Drive with the specified name
     * @param name the name of the folder
     * @param parent a reference to the parent folder
     * @return the reference to the folder or {@code null}
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
     * Returns a reference to the folder in the root of the authenticated user's Google Drive with the specified name
     * @param name the name of the folder
     * @return the reference to the folder or {@code null}
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
     * Returns a list of references to the files inside the specified folder in the authenticated user's Google Drive
     * @param folder a reference to the folder
     * @return a list of references to the files
     * @throws IOException
     */
    private List<ChildReference> processFiles(File folder) throws IOException {

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
     * @throws IOException
     */
    private void deleteFiles(File folder) throws IOException {
        int fileLimit = Config.getKeepCount();

        if (fileLimit == -1) {
            return;
        }

        List<ChildReference> queriedFilesfromDrive = processFiles(folder);
        if (queriedFilesfromDrive.size() > fileLimit) {
            MessageUtil.sendConsoleMessage("There are " + queriedFilesfromDrive.size() + " file(s) which exceeds the " +
                    "limit of " + fileLimit + ", deleting.");

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

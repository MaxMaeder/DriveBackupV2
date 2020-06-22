package ratismal.drivebackup.onedrive;

import io.restassured.response.Response;
import net.kyori.text.TextComponent;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

import org.bukkit.command.CommandSender;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.util.MessageUtil;

import java.io.*;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import static io.restassured.RestAssured.given;

/**
 * Created by Redemption on 2/24/2016.
 */
public class OneDriveUploader {
    private boolean errorOccurred;
    private long totalUploaded;
    private long lastUploaded;
    private String accessToken;
    private String refreshToken;

    private String localBaseFolder;
    private String remoteBaseFolder;

    /**
     * Size of the file chunks to upload to OneDrive
     */
    private static final int CHUNK_SIZE = 5 * 1024 * 1024;

    /**
     * File upload buffer
     */
    private RandomAccessFile raf;

    /**
     * Location of the authenticated user's stored OneDrive refresh token
     */
    private static final String CLIENT_JSON_PATH = DriveBackup.getInstance().getDataFolder().getAbsolutePath()
            + "/OneDriveCredential.json";

    /**
     * OneDrive API credentials
     */
    private static final String CLIENT_ID = "***REMOVED***";

    /**
     * Attempt to authenticate a user with OneDrive using the OAuth 2.0 device authorization grant flow
     * @param plugin a reference to the {@code DriveBackup} plugin
     * @param initiator user who initiated the authentication
     * @throws Exception
     */
    public static void authenticateUser(final DriveBackup plugin, final CommandSender initiator) throws Exception {
        Response response = given()
      		.contentType("application/x-www-form-urlencoded")
      		.param("client_id", CLIENT_ID)
      		.param("scope", "offline_access Files.ReadWrite")
      		.post("https://login.microsoftonline.com/common/oauth2/v2.0/devicecode");

        String verificationUrl = response.getBody().jsonPath().getString("verification_uri");
        String userCode = response.getBody().jsonPath().getString("user_code");
        final String deviceCode = response.getBody().jsonPath().getString("device_code");
        long responseCheckDelay = response.getBody().jsonPath().getLong("interval");

        MessageUtil.sendMessage(initiator, TextComponent.builder()
                .append(
                    TextComponent.of("To link your OneDrive account, go to ")
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
          		    .param("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
          		    .param("device_code", deviceCode)
          		    .post("https://login.microsoftonline.com/common/oauth2/v2.0/token");
        	
                if (response.getStatusCode() == 200) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("refresh_token", response.getBody().jsonPath().getString("refresh_token"));

                    try {
                        FileWriter file = new FileWriter(CLIENT_JSON_PATH);
                        file.write(jsonObject.toString());
                        file.close();
                    } catch (IOException e) {
                        MessageUtil.sendMessage(initiator, "Failed to link your OneDrive account");
                                    
                        Bukkit.getScheduler().cancelTask(task[0]);
                    }
                    
                    MessageUtil.sendMessage(initiator, "Your OneDrive account is linked!");
                    
                    MessageUtil.sendMessage(initiator, "Automatically enabled OneDrive backups");
                    plugin.getConfig().set("onedrive.enabled", true);
                    plugin.saveConfig();
                    
                    DriveBackup.reloadLocalConfig();
                    BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
                    scheduler.cancelTasks(DriveBackup.getInstance());
                    DriveBackup.startThread();
                    
                    Bukkit.getScheduler().cancelTask(task[0]);
                } else if (!response.getBody().jsonPath().getString("error").equals("authorization_pending")) {
                    if (response.getBody().jsonPath().getString("error").equals("expired_token")) {
                        MessageUtil.sendMessage(initiator, "The OneDrive account linking process timed out, please try again");
                    } else {
                        MessageUtil.sendMessage(initiator, "Failed to link your OneDrive account, please try again");
                    }
                    
                    Bukkit.getScheduler().cancelTask(task[0]);
                }
            }
        }, responseCheckDelay * 20L, responseCheckDelay * 20L);
    }
    
    /**
     * Creates an instance of the {@code OneDriveUploader} object using the default base folder paths
     */
    public OneDriveUploader() {
        try {
            setRefreshTokenFromStoredValue();
            retrieveNewAccessToken();
            setRanges(new String[0]);
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }

        localBaseFolder = ".";
        remoteBaseFolder = Config.getDestination();
    }

    /**
     * Creates an instance of the {@code OneDriveUploader} object using the specifed base folder paths
     * @param localBaseFolder the path to the folder which all local file paths are relative to
     * @param remoteBaseFolder the path to the folder which all remote file paths are relative to 
     */
    public OneDriveUploader(String localBaseFolder, String remoteBaseFolder) {
        if (!Config.isOnedriveEnabled()) {
            return;
        }

        try {
            setRefreshTokenFromStoredValue();
            retrieveNewAccessToken();
            setRanges(new String[0]);
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }

        this.localBaseFolder = localBaseFolder;
        this.remoteBaseFolder = remoteBaseFolder;
    }

    /**
     * Sets the authenticated user's stored OneDrive refresh token from the stored value
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
     * Gets a new OneDrive access token for the authenticated user
     */
    private void retrieveNewAccessToken() {
        Response response = given()
            .contentType("application/x-www-form-urlencoded")
            .param("client_id", CLIENT_ID)
            .param("scope", "offline_access Files.ReadWrite")
            .param("refresh_token", returnRefreshToken())
            .param("grant_type", "refresh_token")
            .param("redirect_uri", "https://login.microsoftonline.com/common/oauth2/nativeclient")
            .post("https://login.microsoftonline.com/common/oauth2/v2.0/token");
        setAccessToken(response.getBody().jsonPath().getString("access_token"));
    }

    /**
     * Uploads the specified file to the authenticated user's OneDrive inside a folder for the specified file type
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     */
    public void uploadFile(File file, String type) {
        try {
            resetRanges();
            
            type = type.replace(".."  + File.separator, "");
        	
            if (!checkDestinationExists()) {
                createDestinationFolder();
            }

            if (!checkDestinationExists(type)) {
                createDestinationFolder(type);
            }

            Response openConnection = given()
                .auth().oauth2(returnAccessToken())
                .contentType("application/json")
                // Encodes colons in file names because they confuse the OneDrive API
                .post("https://graph.microsoft.com/v1.0/me/drive/root:/" + (Config.getDestination() + "/" + type + "/" + file.getName()).replace(":", "%3A") + ":/createUploadSession");

            raf = new RandomAccessFile(file, "r");

            String uploadURL = openConnection.getBody().jsonPath().get("uploadUrl");

            Response uploadFile;

            boolean isComplete = false;

            while (!isComplete) {
                byte[] bytesToUpload = getChunk();

                uploadFile = given().contentType("application/zip")
                    .header("Content-Range", String.format("bytes %d-%d/%d", getTotalUploaded(), getTotalUploaded() + bytesToUpload.length - 1, file.length()))
                    .body(bytesToUpload).put(uploadURL);

                if (getTotalUploaded() + bytesToUpload.length < file.length()) {
                    try {
                        List<String> test = uploadFile.getBody().jsonPath().getList("nextExpectedRanges");
                        setRanges(test.toArray(new String[test.size()]));
                    } catch (NullPointerException e) {
                        MessageUtil.sendConsoleException(e);
                    }
                } else {
                    isComplete = true;
                }
            }

            if (checkDestinationExists(type)) {
                deleteFiles(type);
            }

            raf.close();
        } catch(Exception error) {
            MessageUtil.sendConsoleException(error);
            setErrorOccurred(true);
        }
    }

    /**
     * Downloads the specifed file from the authenticated user's OneDrive into a folder for the specified file type
     * @param filePath the path of the file
     * @param type the type of file (ex. plugins, world)
     */
    public void downloadFile(String filePath, String type) {
        try {
            type = type.replace(".."  + File.separator, "");

            Response openConnection = given()
                .auth().oauth2(returnAccessToken())
                .contentType("application/json")
                // Encodes colons in file names because they confuse the OneDrive API
                .post("https://graph.microsoft.com/v1.0/me/drive/root:/" + (remoteBaseFolder + "/" + filePath).replace(":", "%3A") + ":/createUploadSession");

            String downloadURL = openConnection.getHeader("Location");
            Response downloadFile;

            OutputStream outputStream = new FileOutputStream(localBaseFolder + java.io.File.separator + type + java.io.File.separator + new java.io.File(filePath).getName());

            boolean isComplete = false;
            int bytesDownloaded = 0;

            while (!isComplete) {
                downloadFile = given()
                    .header("Range", String.format("bytes=%d-%d", bytesDownloaded, bytesDownloaded + CHUNK_SIZE))
                    .get(downloadURL);

                outputStream.write(downloadFile.getBody().asByteArray());

                if (downloadFile.getStatusCode() == 206) {
                    bytesDownloaded += CHUNK_SIZE;
                } else {
                    isComplete = true;
                }
            }

            outputStream.flush();
            outputStream.close();
        } catch(Exception error) {
            MessageUtil.sendConsoleException(error);
            setErrorOccurred(true);
        }
    }

    /**
     * Returns a list of the paths of the ZIP files and their modification dates inside the specified folder in the authenticated user's OneDrive
     * @param folderPath the path of the folder
     * @return the list of files
     */
    public HashMap<String, Date> getZipFiles(String folderPath) {
        HashMap<String, Date> filePaths = new HashMap<>();

        try {
            Response rootQuery = given()
        	    .auth().oauth2(returnAccessToken())
        	    .get("https://graph.microsoft.com/v1.0/me/drive/root:/" + (remoteBaseFolder + "/" + folderPath).replace(":", "%3A") + ":/children");

            List<String> fileNames = rootQuery.getBody().jsonPath().getList("value.name");
            List<String> fileModificationDates = rootQuery.getBody().jsonPath().getList("value.lastModifiedDateTime");

            for (int i = 0; i < fileNames.size(); i++) {
                if (fileNames.get(i).endsWith(".zip")) {
                    filePaths.put(
                            folderPath + fileNames.get(i), 
                            Date.from(Instant.parse(fileModificationDates.get(i))));
                }
            }
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }

        return filePaths;
    }

    /**
     * Gets whether an error occurred while accessing the authenticated user's OneDrive
     * @return whether an error occurred
     */
    public boolean isErrorWhileUploading() {
        return this.errorOccurred;
    }

    /**
     * Check if the upload destination folder exists in the authenticated user's OneDrive
     * <p>
     * The upload destination folder is specified by the user in the {@code config.yml}
     * @return whether the folder exists
     */
    private boolean checkDestinationExists() {
        Response rootQuery = given()
        	.auth().oauth2(returnAccessToken())
        	.get("https://graph.microsoft.com/v1.0/me/drive/root/children");
        List<String> availableFolders = rootQuery.getBody().jsonPath().getList("value.name");
        return availableFolders.contains(Config.getDestination());
    }

    /**
     * Check if a folder for the specified file type exists within the upload destination folder in the authenticated user's OneDrive
     * <p>
     * The upload destination folder is specified by the user in the {@code config.yml}
     * @return whether the folder exists
     */
    private boolean checkDestinationExists(String type) {
        Response rootQuery = given()
        	.auth().oauth2(returnAccessToken())
        	.get("https://graph.microsoft.com/v1.0/me/drive/root:/" + Config.getDestination() + ":/children");

        try {
            List<String> availableFolders = rootQuery.getBody().jsonPath().getList("value.name");
            return availableFolders.contains(type);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Creates the upload destination folder in the authenticated user's OneDrive
     * <p>
     * The upload destination folder is specified by the user in the {@code config.yml}
     */
    private void createDestinationFolder() {
        given()
        	.auth().oauth2(returnAccessToken())
        	.contentType("application/json")
        	.body("{\"name\": \"" + Config.getDestination() + "\", \"folder\": { }}")
        	.post("https://graph.microsoft.com/v1.0/me/drive/root/children");
    }

    /**
     * Creates a folder for the specified file type exists within the upload destination folder in the authenticated user's OneDrive
     * <p>
     * The upload destination folder is specified by the user in the {@code config.yml}
     */
    private void createDestinationFolder(String type) {
        String id = given()
        	.auth().oauth2(returnAccessToken())
        	.get("https://graph.microsoft.com/v1.0/me/drive/root:/" + Config.getDestination()).jsonPath().getString("id");

        given()
            .auth().oauth2(returnAccessToken())
            .contentType("application/json")
            .body("{" +
            " \"name\": \"" + type + "\"," +
            " \"folder\": {}," +
            " \"@name.conflictBehavior\": \"fail\"" +
            "}")
            .post("https://graph.microsoft.com/v1.0/me/drive/items/" + id + "/children");
    }

    /**
     * Deletes the oldest of the specified type of file past the number to retain from the authenticated user's OneDrive
     * <p>
     * The number of files to retain is specified by the user in the {@code config.yml}
     * @param type the type of file (ex. plugins, world)
     */
    private void deleteFiles(String type) {
        int fileLimit = Config.getKeepCount();

        if (fileLimit == -1) {
            return;
        }

        Response childResponse = given()
        		.auth().oauth2(returnAccessToken())
        		.get("https://graph.microsoft.com/v1.0/me/drive/root:/" + Config.getDestination() + "/" + type + ":/children?sort_by=createdDateTime");

        List<String> availableFileIDs = childResponse.getBody().jsonPath().getList("value.id");

        if(fileLimit < availableFileIDs.size()){
            MessageUtil.sendConsoleMessage("There are " + availableFileIDs.size() + " file(s) which exceeds the " +
                    "limit of " + fileLimit + ", deleting");
        }

        for (Iterator<String> iterator = availableFileIDs.listIterator(); iterator.hasNext(); ) {
            String fileIDValue = iterator.next();
            if (fileLimit < availableFileIDs.size()) {
                MessageUtil.sendConsoleMessage("Removing file with ID: " + fileIDValue);
                given()
                	.auth().oauth2(returnAccessToken())
                	.delete("https://graph.microsoft.com/v1.0/me/drive/items/" + fileIDValue);
                iterator.remove();
            }

            if (availableFileIDs.size() <= fileLimit){
                break;
            }
        }
    }

    /**
     * A range of bytes
     */
    private static class Range {
        private final long start;
        private final long end;

        /**
         * Creates an instance of the {@code Range} object
         * @param start the index of the first byte
         * @param end the index of the last byte
         */
        private Range(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Resets the number of bytes uploaded in the last chunk and the number of bytes uploaded in total
     */
    private void resetRanges() {
    	lastUploaded = 0;
    	totalUploaded = 0;
    }
    
    /**
     * Sets the number of bytes uploaded in the last chunk and the number of bytes uploaded in total from the ranges of bytes the OneDrive API requested to be uploaded last
     * @param stringRanges the ranges of bytes requested
     */
    private void setRanges(String[] stringRanges) {
        Range[] ranges = new Range[stringRanges.length];
        for (int i = 0; i < stringRanges.length; i++) {
            long start = Long.parseLong(stringRanges[i].substring(0, stringRanges[i].indexOf('-')));

            String s = stringRanges[i].substring(stringRanges[i].indexOf('-') + 1);

            long end = 0;
            if (!s.isEmpty()) {
                end = Long.parseLong(s);
            }

            ranges[i] = new Range(start, end);
        }

        if (ranges.length > 0) {
            lastUploaded = ranges[0].start - totalUploaded;
            totalUploaded = ranges[0].start;
        }
    }

    /**
     * Gets an array of bytes to upload next from the file buffer based on the number of bytes uploaded so far
     * @return the array of bytes
     * @throws IOException
     */
    private byte[] getChunk() throws IOException {

        byte[] bytes = new byte[CHUNK_SIZE];

        raf.seek(totalUploaded);
        int read = raf.read(bytes);

        if (read < CHUNK_SIZE) {
            bytes = Arrays.copyOf(bytes, read);
        }

        return bytes;
    }

    /**
     * Gets the authenticated user's stored OneDrive credentials
     * <p>
     * The refresh token is stored in {@code /OneDriveCredential.json}
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
     * Formats the specified number of bytes as a readable file size
     * @param size the number of bytes
     * @return a {@code String} containing the readable file size
     */
    private static String readableFileSize(long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    /**
     * Sets whether an error occurred while accessing the authenticated user's OneDrive
     * @param errorOccurredValue whether an error occurred
     */
    private void setErrorOccurred(boolean errorOccurredValue) {
        this.errorOccurred = errorOccurredValue;
    }

    /**
     * Gets the number of bytes uploaded in total
     * @return the number of bytes
     */
    private long getTotalUploaded() {
        return totalUploaded;
    }

    /**
     * Gets the number of bytes uploaded in the last chunk
     * @return the number of bytes
     */
    private long getLastUploaded() {
        return lastUploaded;
    }

    /**
     * Sets the access token of the authenticated user
     * @param accessTokenValue the access token
     */
    private void setAccessToken(String accessTokenValue) {
        this.accessToken = accessTokenValue;
    }

    /**
     * Sets the refresh token of the authenticated user
     * @param refreshTokenValue the refresh token
     */
    private void setRefreshToken(String refreshTokenValue) {
        this.refreshToken = refreshTokenValue;
    }

    /**
     * Gets the access token of the authenticated user
     * @return the access token
     */
    private String returnAccessToken() {
        return this.accessToken;
    }

    /**
     * Gets the refresh token of the authenticated user
     * @return the refresh token
     */
    private String returnRefreshToken() {
        return this.refreshToken;
    }
}

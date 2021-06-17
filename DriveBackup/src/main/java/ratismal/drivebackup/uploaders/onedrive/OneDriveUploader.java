package ratismal.drivebackup.uploaders.onedrive;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.bukkit.Bukkit;

import org.bukkit.command.CommandSender;
import org.json.JSONArray;
import org.json.JSONObject;

import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.plugin.Scheduler;
import ratismal.drivebackup.util.HttpLogger;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.SchedulerUtil;

import java.io.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Redemption on 2/24/2016.
 */
public class OneDriveUploader implements Uploader {
    private boolean errorOccurred;
    private long totalUploaded;
    private long lastUploaded;
    private String accessToken;
    private String refreshToken;

    /**
     * Global instance of the HTTP client
     */
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(3, TimeUnit.MINUTES)
        .readTimeout(3, TimeUnit.MINUTES)
        .build();
    private static final MediaType zipMediaType = MediaType.parse("application/zip; charset=utf-8");
    private static final MediaType jsonMediaType = MediaType.parse("application/json; charset=utf-8");

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
        RequestBody requestBody = new FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", "offline_access Files.ReadWrite")
            .build();

        Request request = new Request.Builder()
            .url("https://login.microsoftonline.com/common/oauth2/v2.0/devicecode")
            .post(requestBody)
            .build();

        Response response = httpClient.newCall(request).execute();
        JSONObject parsedResponse = new JSONObject(response.body().string());
        response.close();

        String verificationUrl = parsedResponse.getString("verification_uri");
        String userCode = parsedResponse.getString("user_code");
        final String deviceCode = parsedResponse.getString("device_code");
        long responseCheckDelay = SchedulerUtil.sToTicks(parsedResponse.getLong("interval"));

        MessageUtil.sendMessage(initiator, Component.text()
            .append(
                Component.text("To link your OneDrive account, go to ")
                .color(NamedTextColor.DARK_AQUA)
            )
            .append(
                Component.text(verificationUrl)
                .color(NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Go to URL")))
                .clickEvent(ClickEvent.openUrl(verificationUrl))
            )
            .append(
                Component.text(" and enter code ")
                .color(NamedTextColor.DARK_AQUA)
            )
            .append(
                Component.text(userCode)
                .color(NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Copy code")))
                .clickEvent(ClickEvent.copyToClipboard(userCode))
            )
            .build());

        final int[] task = new int[]{-1};
        task[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {

                RequestBody requestBody = new FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .add("device_code", deviceCode)
                    .build();
    
                Request request = new Request.Builder()
                    .url("https://login.microsoftonline.com/common/oauth2/v2.0/token")
                    .post(requestBody)
                    .build();
        
                JSONObject parsedResponse = null;
                try {
                    Response response = httpClient.newCall(request).execute();
                    parsedResponse = new JSONObject(response.body().string());
                } catch (Exception exception) {
                    MessageUtil.sendMessage(initiator, "Failed to link your OneDrive account, please try again");

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
                        MessageUtil.sendMessage(initiator, "Failed to link your OneDrive account, please try again");
                                    
                        Bukkit.getScheduler().cancelTask(task[0]);
                    }
                    
                    MessageUtil.sendMessage(initiator, "Your OneDrive account is linked!");
                    
                    if (!plugin.getConfig().getBoolean("onedrive.enabled")) {
                        MessageUtil.sendMessage(initiator, "Automatically enabled OneDrive backups");
                        plugin.getConfig().set("onedrive.enabled", true);
                        plugin.saveConfig();
                        
                        DriveBackup.reloadLocalConfig();
                    }
                    
                    Bukkit.getScheduler().cancelTask(task[0]);
                } else if (!parsedResponse.getString("error").equals("authorization_pending")) {
                    if (parsedResponse.getString("error").equals("expired_token")) {
                        MessageUtil.sendMessage(initiator, "The OneDrive account linking process timed out, please try again");
                    } else {
                        MessageUtil.sendMessage(initiator, "Failed to link your OneDrive account, please try again");
                    }
                    
                    Bukkit.getScheduler().cancelTask(task[0]);
                }

                if (response != null) {
                    response.close();
                }
            }
        }, responseCheckDelay, responseCheckDelay);
    }
    
    /**
     * Creates an instance of the {@code OneDriveUploader} object
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
    }

    /**
     * Sets the authenticated user's stored OneDrive refresh token from the stored value
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
     * Gets a new OneDrive access token for the authenticated user
     */
    private void retrieveNewAccessToken() throws Exception {
        RequestBody requestBody = new FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", "offline_access Files.ReadWrite")
            .add("refresh_token", returnRefreshToken())
            .add("grant_type", "refresh_token")
            .add("redirect_uri", "https://login.microsoftonline.com/common/oauth2/nativeclient")
            .build();

        Request request = new Request.Builder()
            .url("https://login.microsoftonline.com/common/oauth2/v2.0/token")
            .post(requestBody)
            .build();

        Response response = httpClient.newCall(request).execute();
        JSONObject parsedResponse = new JSONObject(response.body().string());
        response.close();

        setAccessToken(parsedResponse.getString("access_token"));
    }

    /**
     * Tests the OneDrive account by uploading a small file
     *  @param testFile the file to upload during the test
     */
    public void test(java.io.File testFile) {
        try {
            String destination = Config.getDestination();
            
            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + returnAccessToken())
                .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + destination + "/" + testFile.getName() + ":/content")
                .put(RequestBody.create(testFile, MediaType.parse("plain/txt")))
                .build();

            Response response = httpClient.newCall(request).execute();
            int statusCode = response.code();
            response.close();

            if (statusCode != 201) {
                setErrorOccurred(true);
            }
            
            TimeUnit.SECONDS.sleep(5);
                
            request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + returnAccessToken())
                .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + destination + "/" + testFile.getName() + ":/")
                .delete()
                .build();
            
            response = httpClient.newCall(request).execute();
            statusCode = response.code();
            response.close();

            if (statusCode != 204) {
                setErrorOccurred(true);
            }
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Uploads the specified file to the authenticated user's OneDrive inside a folder for the specified file type
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     */
    public void uploadFile(java.io.File file, String type) throws Exception {
        try {
            resetRanges();

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

            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + returnAccessToken())
                .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + folder.getPath() + "/" + file.getName() + ":/createUploadSession")
                .post(RequestBody.create("{}", jsonMediaType))
                .build();

            Response response = httpClient.newCall(request).execute();
            JSONObject parsedResponse = new JSONObject(response.body().string());
            response.close();

            String uploadURL = parsedResponse.getString("uploadUrl");

            //Assign our backup to Random Access File
            raf = new RandomAccessFile(file, "r");

            boolean isComplete = false;

            while (!isComplete) {
                byte[] bytesToUpload = getChunk();

                request = new Request.Builder()
                    .addHeader("Content-Range", String.format("bytes %d-%d/%d", getTotalUploaded(), getTotalUploaded() + bytesToUpload.length - 1, file.length()))
                    .url(uploadURL)
                    .put(RequestBody.create(bytesToUpload, zipMediaType))
                    .build();

                response = httpClient.newCall(request).execute();

                if (getTotalUploaded() + bytesToUpload.length < file.length()) {
                    try {
                        parsedResponse = new JSONObject(response.body().string());

                        List<String> nextExpectedRanges = (List<String>) (Object) parsedResponse.getJSONArray("nextExpectedRanges").toList();

                        setRanges(nextExpectedRanges.toArray(new String[nextExpectedRanges.size()]));
                    } catch (NullPointerException e) {
                        MessageUtil.sendConsoleException(e);
                    } 
                } else {
                    isComplete = true;
                }

                response.close();
            }

            deleteFiles(folder);
        } catch(Exception error) {
            MessageUtil.sendConsoleException(error);
            setErrorOccurred(true);
        }

        raf.close();
    }

    /**
     * Gets whether an error occurred while accessing the authenticated user's OneDrive
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
        return "OneDrive";
    }

    /**
     * Gets the setup instructions for this uploaders
     * @return a Component explaining how to set up this uploader
     */
    public Component getSetupInstructions()
    {
        return Component.text()
            .append(
                Component.text("Failed to backup to OneDrive, please run ")
                .color(NamedTextColor.DARK_AQUA)
            )
            .append(
                Component.text("/drivebackup linkaccount onedrive")
                .color(NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Run command")))
                .clickEvent(ClickEvent.runCommand("/drivebackup linkaccount onedrive"))
            )
            .build();
    }


    /**
     * Creates a folder with the specified name in the specified parent folder in the authenticated user's OneDrive
     * @param name the name of the folder
     * @param parent the parent folder
     * @return the created folder
     * @throws Exception
     */
    private File createFolder(String name, File parent) throws Exception {
        File file = getFolder(name, parent);
        if (file != null) {
            return file;
        }

        Request request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + returnAccessToken())
            .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + parent.getPath())
            .build();

        Response response = httpClient.newCall(request).execute();
        JSONObject parsedResponse = new JSONObject(response.body().string());
        response.close();

        String parentId = parsedResponse.getString("id");

        RequestBody requestBody = RequestBody.create(
            "{" +
            " \"name\": \"" + name + "\"," +
            " \"folder\": {}," +
            " \"@name.conflictBehavior\": \"fail\"" +
            "}", jsonMediaType);

        request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + returnAccessToken())
            .url("https://graph.microsoft.com/v1.0/me/drive/items/" + parentId + "/children")
            .post(requestBody)
            .build();

        response = httpClient.newCall(request).execute();
        boolean folderCreated = response.isSuccessful();
        response.close();

        if (!folderCreated) {
            throw new Exception("Couldn't create folder " + name);
        }
            
        return parent.add(name);
    }

    /**
     * Creates a folder with the specified name in the root of the authenticated user's OneDrive
     * @param name the name of the folder
     * @return the created folder
     * @throws Exception
     */
    private File createFolder(String name) throws Exception {
        File file = getFolder(name);
        if (file != null) {
            return file;
        }

        RequestBody requestBody = RequestBody.create(
            "{" +
            " \"name\": \"" + name + "\"," +
            " \"folder\": {}," +
            " \"@name.conflictBehavior\": \"fail\"" +
            "}", jsonMediaType);

        Request request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + returnAccessToken())
            .url("https://graph.microsoft.com/v1.0/me/drive/root/children")
            .post(requestBody)
            .build();

        Response response = httpClient.newCall(request).execute();
        boolean folderCreated = response.isSuccessful();
        response.close();

        if (!folderCreated) {
            throw new Exception("Couldn't create folder " + name);
        }

        return new File().add(name);
    }

    /**
     * Returns the folder in the specified parent folder of the authenticated user's OneDrive with the specified name
     * @param name the name of the folder
     * @param parent the parent folder
     * @return the folder or {@code null}
     */
    private File getFolder(String name, File parent) {
        try {
            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + returnAccessToken())
                .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + parent.getPath() + ":/children")
                .build();

            Response response = httpClient.newCall(request).execute();
            JSONObject parsedResponse = new JSONObject(response.body().string());
            response.close();

            JSONArray jsonArray = parsedResponse.getJSONArray("value");

            for (int i = 0; i < jsonArray.length(); i++) {
                String folderName = jsonArray.getJSONObject(i).getString("name");

                if (name.equals(folderName)) {
                    return parent.add(name);
                }
            }
            
        } catch (Exception exception) {}

        return null;
    }

    /**
     * Returns the folder in the root of the authenticated user's OneDrive with the specified name
     * @param name the name of the folder
     * @return the folder or {@code null}
     */
    private File getFolder(String name) {
        try {
            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + returnAccessToken())
                .url("https://graph.microsoft.com/v1.0/me/drive/root/children")
                .build();

            Response response = httpClient.newCall(request).execute();
            JSONObject parsedResponse = new JSONObject(response.body().string());
            response.close();

            JSONArray jsonArray = parsedResponse.getJSONArray("value");

            for (int i = 0; i < jsonArray.length(); i++) {
                String folderName = jsonArray.getJSONObject(i).getString("name");

                if (name.equals(folderName)) {
                    return new File().add(name);
                }
            }
            
        } catch (Exception exception) {}

        return null;
    }

    /**
     * Deletes the oldest files in the specified folder past the number to retain from the authenticated user's OneDrive
     * <p>
     * The number of files to retain is specified by the user in the {@code config.yml}
     * @param folder the folder containing the files
     * @throws Exception
     */
    private void deleteFiles(File parent) throws Exception {
        int fileLimit = Config.getKeepCount();

        if (fileLimit == -1) {
            return;
        }

        Request request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + returnAccessToken())
            .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + parent.getPath() + ":/children?sort_by=createdDateTime")
            .build();

        Response response = httpClient.newCall(request).execute();
        JSONObject parsedResponse = new JSONObject(response.body().string());
        response.close();

        ArrayList<String> availableFileIDs = new ArrayList<>();

        JSONArray jsonArray = parsedResponse.getJSONArray("value");
        for (int i = 0; i < jsonArray.length(); i++) {
            availableFileIDs.add(jsonArray.getJSONObject(i).getString("id"));
        }

        if(fileLimit < availableFileIDs.size()){
            MessageUtil.sendConsoleMessage("There are " + availableFileIDs.size() + " file(s) which exceeds the limit of " + fileLimit + ", deleting");
        }

        for (Iterator<String> iterator = availableFileIDs.listIterator(); iterator.hasNext(); ) {
            String fileIDValue = iterator.next();
            if (fileLimit < availableFileIDs.size()) {
                request = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + returnAccessToken())
                    .url("https://graph.microsoft.com/v1.0/me/drive/items/" + fileIDValue)
                    .delete()
                    .build();

                httpClient.newCall(request).execute().close();
                    
                iterator.remove();
            }

            if (availableFileIDs.size() <= fileLimit){
                break;
            }
        }
    }

    /**
     * A file/folder in the authenticated user's OneDrive 
     */
    private static final class File {
        private ArrayList<String> filePath = new ArrayList<>();

        /**
         * Creates a reference of the {@code File} object
         */
        File() {
        }

        /**
         * Returns a {@code File} with the specified folder added to the file path
         * @param folder the {@code File}
         */
        private File add(String folder) {
            File childFile = new File();
            if (getPath().isEmpty()) {
                childFile.setPath(folder);
            } else {
                childFile.setPath(getPath() + "/" + folder);
            }

            return childFile;
        }

        /**
         * Sets the path of the file/folder
         * @param path the path, as an {@code String}
         */
        private void setPath(String path) {
            filePath.clear();
            Collections.addAll(filePath, path.split("/"));
        }

        /**
         * Gets the path of the file/folder
         * @return the path, as a {@code String}
         */
        private String getPath() {
            return String.join("/", filePath);
        }

        /**
         * Gets the name of the file/folder
         * @return the name, including any file extensions
         */
        private String getName() {
            return filePath.get(filePath.size() - 1);
        }

        /**
         * Gets the path of the parent folder of the file/folder
         * @return the path, as a String
         */
        private String getParent() {
            ArrayList<String> parentPath = new ArrayList<>(filePath);
            parentPath.remove(parentPath.size() - 1);

            return String.join("/", parentPath);
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

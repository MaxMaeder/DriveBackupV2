package ratismal.drivebackup.dropbox;

import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.Uploader;
import ratismal.drivebackup.config.Config;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.bukkit.command.CommandSender;
import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.json.JSONArray;
import org.json.JSONObject;

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

public class DropboxUploader implements Uploader {
    private boolean errorOccurred;

    /**
     * Global instance of the HTTP client
     */
    private static final OkHttpClient httpClient = new OkHttpClient();

    /**
     * Location of the authenticated user's stored Dropbox refresh token
     */
    private static final String CLIENT_JSON_PATH = DriveBackup.getInstance().getDataFolder().getAbsolutePath()
        + "/DropboxCredential.json";

    /**
     * Global Dropbox tokens
     */
    private String accessToken;
    private String refreshToken;

    /**
     * Dropbox API credentials
     */
    private static final String APP_KEY = "***REMOVED***";
    private static final String APP_SECRET = "***REMOVED***";

    /**
     * Attempt to authenticate a user with Dropbox using the OAuth 2.0 device
     * authorization grant flow
     * 
     * @param plugin    a reference to the {@code DriveBackup} plugin
     * @param initiator user who initiated the authentication
     * @throws Exception
     */
    public static void authenticateUser(final DriveBackup plugin, final CommandSender initiator) throws Exception {

        Boolean[] errorOccured = {false};
        final String authorizeUrl = "https://www.dropbox.com/oauth2/authorize?token_access_type=offline&response_type=code&client_id="+APP_KEY;

        MessageUtil.sendMessage(initiator,
            Component.text()
                .append(Component.text("To link your Dropbox account, go to ").color(NamedTextColor.DARK_AQUA))
                .append(Component.text(authorizeUrl).color(NamedTextColor.GOLD)
                    .hoverEvent(HoverEvent.showText(Component.text("Go to URL")))
                    .clickEvent(ClickEvent.openUrl(authorizeUrl)))
                .append(Component.text(" and paste the code here:").color(NamedTextColor.DARK_AQUA))
                .build());

        final Prompt getToken = new StringPrompt() {

            @Override
            public String getPromptText(final ConversationContext context) {
                return "";
            }

            @Override
            public Prompt acceptInput(final ConversationContext context, String input) {
                try {
                    input = input.trim();

                    RequestBody requestBody = new FormBody.Builder()
                        .add("code", input)
                        .add("grant_type", "authorization_code")
                        .add("client_id", APP_KEY)
                        .add("client_secret", APP_SECRET).build();

                    Request request = new Request.Builder()
                        .url("https://api.dropbox.com/oauth2/token")
                        .post(requestBody)
                        .build();

                    JSONObject parsedResponse = null;
                    try {
                        Response response = httpClient.newCall(request).execute();
                        parsedResponse = new JSONObject(response.body().string());
                        response.close();
                    } catch (Exception exception) {
                        errorOccured[0] = true;
                    }

                    if (parsedResponse.has("refresh_token")) {
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("refresh_token", parsedResponse.getString("refresh_token"));

                        try {
                            FileWriter file = new FileWriter(CLIENT_JSON_PATH);
                            file.write(jsonObject.toString());
                            file.close();
                        } catch (IOException e) {
                            errorOccured[0] = true;
                        }

                    } else if (parsedResponse.has("error")) {
                        errorOccured[0] = true;
                    }

                } catch (final Exception ex) {
                    errorOccured[0] = true;
                }
                return Prompt.END_OF_CONVERSATION;
            }
        };

        final ConversationFactory factory = new ConversationFactory(plugin)
            .withTimeout(60)
            .withLocalEcho(false)
            .withFirstPrompt(getToken)
            .addConversationAbandonedListener((ConversationAbandonedEvent abandonedEvent) -> {
                if (abandonedEvent.gracefulExit()) {
                    if (!errorOccured[0]) {
                        MessageUtil.sendMessage(initiator, "Your Dropbox account is linked!");

                        if (!plugin.getConfig().getBoolean("dropbox.enabled")) {
                            MessageUtil.sendMessage(initiator, "Automatically enabled Dropbox backups");
                            plugin.getConfig().set("dropbox.enabled", true);
                            plugin.saveConfig();

                            DriveBackup.reloadLocalConfig();
                            DriveBackup.startThread();
                        }
                    } else {
                        MessageUtil.sendMessage(initiator, "Failed to link your Dropbox account, please try again");
                    }
                } else {
                    MessageUtil.sendMessage(initiator, "Abandoned Dropbox account linking");
                }
            });

        Conversation conversation = factory.buildConversation((Conversable) initiator);
        conversation.begin();        
    }

    /**
     * Tests the Dropbox account by uploading a small file
     *  @param testFile the file to upload during the test
     */
    public void test(java.io.File testFile) {
        try (DataInputStream dis = new DataInputStream(new FileInputStream(testFile))) {
            byte[] content = new byte[dis.available()];
            dis.readFully(content);

            MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");

            RequestBody requestBody = RequestBody.create(content, OCTET_STREAM);
            String destination = Config.getDestination();

            JSONObject dropbox_json = new JSONObject();
            dropbox_json.put("path", "/" + destination + "/" + testFile.getName());
            String dropbox_arg = dropbox_json.toString();

            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + returnAccessToken())
                .addHeader("Dropbox-API-Arg", dropbox_arg)
                .url("https://content.dropboxapi.com/2/files/upload")
                .post(requestBody)
                .build();

            Response response = httpClient.newCall(request).execute();
            int statusCode = response.code();
            response.close();
    
            if (statusCode != 200) {
                setErrorOccurred(true);
            }
            
            TimeUnit.SECONDS.sleep(5);

            JSONObject deleteJson = new JSONObject();
            deleteJson.put("path", "/" + destination + "/" + testFile.getName());
            RequestBody deleteRequestBody = RequestBody.create(deleteJson.toString(), JSON);

            request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + returnAccessToken())
                .url("https://api.dropboxapi.com/2/files/delete_v2")
                .post(deleteRequestBody)
                .build();

            response = httpClient.newCall(request).execute();
            statusCode = response.code();
            response.close();
        
            if (statusCode != 200) {
                setErrorOccurred(true);
            }
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Uploads the specified file to the authenticated user's Dropbox inside a
     * folder for the specified file type
     * 
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     */
    public void uploadFile(final java.io.File file, final String type) {
        String destination = Config.getDestination();
        int fileSize = (int) file.length();
        int fileSizeInMB =  fileSize / (1024*1024);
        MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");

        try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
            Boolean chunkeduploadEnabled = false;
            if (chunkeduploadEnabled) {
                //More than 150MB - Chunked upload
                final int CHUNKED_UPLOAD_CHUNK_SIZE = (1024 * 1024 * 10); //10 MB chunk
                final int CHUNKED_UPLOAD_MAX_ATTEMPTS = 5;
                int uploaded = 0;
                byte[] buff = new byte[CHUNKED_UPLOAD_CHUNK_SIZE];
                String sessionId = null;

                for (int i = 0; i < CHUNKED_UPLOAD_MAX_ATTEMPTS; ++i) {
                    // (1) Start
                    if (sessionId == null) {

                        dis.read(buff, 0, CHUNKED_UPLOAD_CHUNK_SIZE);
                        RequestBody requestBody = RequestBody.create(buff, OCTET_STREAM);

                        Request request = new Request.Builder()
                            .addHeader("Authorization", "Bearer " + returnAccessToken())
                            .post(requestBody)
                            .url("https://content.dropboxapi.com/2/files/upload_session/start")
                            .build();

                        Response response = httpClient.newCall(request).execute();
                        uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;
                        JSONObject parsedResponse = new JSONObject(response.body().string());
                        sessionId = parsedResponse.getString("session_id");
                        response.close();
                    }

                    // (2) Append
                    while ((dis.available() - uploaded) > CHUNKED_UPLOAD_CHUNK_SIZE) {
                        dis.read(buff, 0, buff.length);
                        RequestBody requestBody = RequestBody.create(buff, OCTET_STREAM);

                        JSONObject dropbox_cursor = new JSONObject();
                        dropbox_cursor.put("session_id", sessionId);
                        dropbox_cursor.put("offset", uploaded);

                        JSONObject dropbox_json = new JSONObject();
                        dropbox_json.put("cursor", dropbox_cursor);
                        String dropbox_arg = dropbox_json.toString();

                        Request request = new Request.Builder()
                            .addHeader("Dropbox-API-Arg", dropbox_arg)
                            .addHeader("Authorization", "Bearer " + returnAccessToken())
                            .post(requestBody)
                            .url("https://content.dropboxapi.com/2/files/upload_session/append_v2")                                    .build();

                        Response response = httpClient.newCall(request).execute();
                        response.close();
                        uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;
                    }

                    // (3) Finish
                    int remaining = fileSize - uploaded;

                    byte[] lastchunk = new byte[remaining];
                    dis.skip(uploaded);
                    dis.read(lastchunk, 0, lastchunk.length);
                    RequestBody requestBody = RequestBody.create(lastchunk, OCTET_STREAM);

                    JSONObject dropboxCursorJson = new JSONObject();
                    dropboxCursorJson.put("session_id", sessionId);
                    dropboxCursorJson.put("offset", uploaded);

                    JSONObject dropboxCommitJson = new JSONObject();
                    dropboxCommitJson.put("path", "/" + destination + "/" + type + "/" + file.getName());

                    JSONObject dropboxJson = new JSONObject();
                    dropboxJson.put("cursor", dropboxCursorJson);
                    dropboxJson.put("commit", dropboxCommitJson);
                    String dropbox_arg = dropboxJson.toString();

                    Request request = new Request.Builder()
                        .addHeader("Dropbox-API-Arg", dropbox_arg)
                        .addHeader("Authorization", "Bearer " + returnAccessToken())
                        .post(requestBody)
                        .url("https://content.dropboxapi.com/2/files/upload_session/finish")
                        .build();

                    Response response = httpClient.newCall(request).execute();
                    response.close();

                    deleteFiles(type);
                    return;
                }
            } else {
                //Less than 150MB - Single upload

                byte[] content = new byte[dis.available()];
                dis.readFully(content);
                RequestBody requestBody = RequestBody.create(content, OCTET_STREAM);

                JSONObject dropbox_json = new JSONObject();
                dropbox_json.put("path", "/" + destination + "/" + type + "/" + file.getName());
                String dropbox_arg = dropbox_json.toString();

                Request request = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + returnAccessToken())
                    .addHeader("Dropbox-API-Arg", dropbox_arg)
                    .url("https://content.dropboxapi.com/2/files/upload")
                    .post(requestBody)
                    .build();

                Response response = httpClient.newCall(request).execute();
                response.close();

                deleteFiles(type);
            }
        } catch (Exception e) {
            e.printStackTrace();
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Deletes the oldest files past the number to retain from the FTP server inside
     * the specified folder for the file type
     * <p>
     * The number of files to retain is specified by the user in the
     * {@code config.yml}
     * 
     * @param type the type of file (ex. plugins, world)
     * @throws Exception
     */
    private void deleteFiles(String type) throws Exception {
        String destination = Config.getDestination();
        int fileLimit = Config.getKeepCount();
        if (fileLimit == -1) {
            return;
        }
        
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        JSONObject json = new JSONObject();
        json.put("path", "/" + destination + "/" + type);
        RequestBody requestBody = RequestBody.create(json.toString(), JSON);
        
        Request request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + returnAccessToken())
            .url("https://api.dropboxapi.com/2/files/list_folder")
            .post(requestBody)
            .build();

        Response response = httpClient.newCall(request).execute();
        JSONObject parsedResponse = new JSONObject(response.body().string());
        JSONArray files = parsedResponse.getJSONArray("entries");
        response.close();

        if (files.length() > fileLimit) {
            MessageUtil.sendConsoleMessage("There are " + files.length() + " file(s) which exceeds the limit of " + fileLimit + ", deleting");
            while (files.length() > fileLimit) {
                JSONObject deleteJson = new JSONObject();
                deleteJson.put("path", "/" + destination + "/" + type + "/" + files.getJSONObject(0).get("name"));
                RequestBody deleteRequestBody = RequestBody.create(deleteJson.toString(), JSON);

                Request deleteRequest = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + returnAccessToken())
                    .url("https://api.dropboxapi.com/2/files/delete_v2")
                    .post(deleteRequestBody)
                    .build();

                Response deleteResponse = httpClient.newCall(deleteRequest).execute();
                deleteResponse.close();
                
                files.remove(0);
            }
        }
    }

    /**
     * Creates an instance of the {@code DropboxUploader} object
     */
    public DropboxUploader() {
        try {
            setRefreshTokenFromStoredValue();
            retrieveNewAccessToken();
        } catch (final Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Gets a new Dropbox access token for the authenticated user
     */
    private void retrieveNewAccessToken() throws Exception {
        RequestBody requestBody = new FormBody.Builder()
            .add("client_id", APP_KEY)
            .add("client_secret", APP_SECRET)
            .add("refresh_token", returnRefreshToken())
            .add("grant_type", "refresh_token")
            .build();

        Request request = new Request.Builder()
            .url("https://api.dropbox.com/oauth2/token")
            .post(requestBody)
            .build();

        Response response = httpClient.newCall(request).execute();
        JSONObject parsedResponse = new JSONObject(response.body().string());
        response.close();

        setAccessToken(parsedResponse.getString("access_token"));
    }

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
     * 
     * @return name of upload service
     */
    public String getName() {
        return "Dropbox";
    }

    /**
     * Gets the setup instructions for this uploaders
     * 
     * @return a Component explaining how to set up this uploader
     */
    public Component getSetupInstructions() {
        return Component.text()
            .append(Component.text("Failed to backup to Dropbox, please run ").color(NamedTextColor.DARK_AQUA))
            .append(Component.text("/drivebackup linkaccount dropbox").color(NamedTextColor.GOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Run command")))
                .clickEvent(ClickEvent.runCommand("/drivebackup linkaccount dropbox")))
            .build();
    }

    /**
     * Gets the authenticated user's stored Dropbox credentials
     * <p>
     * The refresh token is stored in {@code /DropboxCredential.json}
     * 
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
     * Sets whether an error occurred while accessing the authenticated user's
     * Dropbox
     * 
     * @param errorOccurredValue whether an error occurred
     */
    private void setErrorOccurred(final boolean errorOccurredValue) {
        this.errorOccurred = errorOccurredValue;
    }

    /**
     * Sets the authenticated user's stored Dropbox refresh token from the stored
     * value
     * 
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
     * Sets the access token of the authenticated user
     * 
     * @param accessTokenValue the access token
     */
    private void setAccessToken(String accessTokenValue) {
        this.accessToken = accessTokenValue;
    }

    /**
     * Sets the refresh token of the authenticated user
     * 
     * @param refreshTokenValue the refresh token
     */
    private void setRefreshToken(String refreshTokenValue) {
        this.refreshToken = refreshTokenValue;
    }

    /**
     * Gets the access token of the authenticated user
     * 
     * @return the access token
     */
    private String returnAccessToken() {
        return this.accessToken;
    }

    /**
     * Gets the refresh token of the authenticated user
     * 
     * @return the refresh token
     */
    private String returnRefreshToken() {
        return this.refreshToken;
    }
}
package ratismal.drivebackup.uploaders.dropbox;

import main.java.credentials.DropboxCredentials;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.NetUtil;
import ratismal.drivebackup.uploaders.Authenticator;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.uploaders.Authenticator.AuthenticationProvider;
import ratismal.drivebackup.UploadThread.UploadLogger;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static ratismal.drivebackup.config.Localization.intl;

public class DropboxUploader implements Uploader {
    private UploadLogger logger;
    private boolean errorOccurred;

    public static final String UPLOADER_NAME = "Dropbox";

    /**
     * Global instance of the HTTP client
     */
    private static final OkHttpClient httpClient = new OkHttpClient();

    /**
     * Global Dropbox tokens
     */
    private String accessToken = "";
    private String refreshToken;

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
            String destination = ConfigParser.getConfig().backupStorage.remoteDirectory;

            JSONObject dropbox_json = new JSONObject();
            dropbox_json.put("path", "/" + destination + "/" + testFile.getName());
            String dropbox_arg = dropbox_json.toString();

            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
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
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://api.dropboxapi.com/2/files/delete_v2")
                .post(deleteRequestBody)
                .build();

            response = httpClient.newCall(request).execute();
            statusCode = response.code();
            response.close();
        
            if (statusCode != 200) {
                setErrorOccurred(true);
            }
        } catch (Exception exception) {
            NetUtil.catchException(exception, "api.dropboxapi.com", logger);
            MessageUtil.sendConsoleException(exception);
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
        String destination = ConfigParser.getConfig().backupStorage.remoteDirectory;
        int fileSize = (int) file.length();
        MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");

        String folder = type.replaceAll("\\.{1,2}\\/", "");

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
                            .addHeader("Authorization", "Bearer " + accessToken)
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
                            .addHeader("Authorization", "Bearer " + accessToken)
                            .post(requestBody)
                            .url("https://content.dropboxapi.com/2/files/upload_session/append_v2")
                            .build();

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
                    dropboxCommitJson.put("path", "/" + destination + "/" + folder + "/" + file.getName());

                    JSONObject dropboxJson = new JSONObject();
                    dropboxJson.put("cursor", dropboxCursorJson);
                    dropboxJson.put("commit", dropboxCommitJson);
                    String dropbox_arg = dropboxJson.toString();

                    Request request = new Request.Builder()
                        .addHeader("Dropbox-API-Arg", dropbox_arg)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .post(requestBody)
                        .url("https://content.dropboxapi.com/2/files/upload_session/finish")
                        .build();

                    Response response = httpClient.newCall(request).execute();
                    response.close();

                    pruneBackups(folder);
                    return;
                }
            } else {
                //Less than 150MB - Single upload

                byte[] content = new byte[dis.available()];
                dis.readFully(content);
                RequestBody requestBody = RequestBody.create(content, OCTET_STREAM);

                JSONObject dropbox_json = new JSONObject();
                dropbox_json.put("path", "/" + destination + "/" + folder + "/" + file.getName());
                String dropbox_arg = dropbox_json.toString();

                Request request = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Dropbox-API-Arg", dropbox_arg)
                    .url("https://content.dropboxapi.com/2/files/upload")
                    .post(requestBody)
                    .build();

                Response response = httpClient.newCall(request).execute();
                response.close();

                try {
                    pruneBackups(folder);
                } catch (Exception e) {
                    logger.log(intl("backup-method-prune-failed"));
                    
                    throw e;
                }
            }
        } catch (Exception exception) {
            NetUtil.catchException(exception, "api.dropboxapi.com", logger);
            MessageUtil.sendConsoleException(exception);
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
    private void pruneBackups(String type) throws Exception {
        Config config = ConfigParser.getConfig();

        String destination = config.backupStorage.remoteDirectory;
        int fileLimit = config.backupStorage.keepCount;
        if (fileLimit == -1) {
            return;
        }
        
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        JSONObject json = new JSONObject();
        json.put("path", "/" + destination + "/" + type);
        RequestBody requestBody = RequestBody.create(json.toString(), JSON);
        
        Request request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://api.dropboxapi.com/2/files/list_folder")
            .post(requestBody)
            .build();

        Response response = httpClient.newCall(request).execute();
        JSONObject parsedResponse = new JSONObject(response.body().string());
        JSONArray files = parsedResponse.getJSONArray("entries");
        response.close();

        if (files.length() > fileLimit) {
            logger.info(
                intl("backup-method-limit-reached"), 
                "file-count", String.valueOf(files.length()),
                "upload-method", getName(),
                "file-limit", String.valueOf(fileLimit));

            while (files.length() > fileLimit) {
                JSONObject deleteJson = new JSONObject();
                deleteJson.put("path", "/" + destination + "/" + type + "/" + files.getJSONObject(0).get("name"));
                RequestBody deleteRequestBody = RequestBody.create(deleteJson.toString(), JSON);

                Request deleteRequest = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + accessToken)
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
    public DropboxUploader(UploadLogger logger) {
        this.logger = logger;

        try {
            refreshToken = Authenticator.getRefreshToken(AuthenticationProvider.DROPBOX);
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
            .add("client_id", DropboxCredentials.CLIENT_ID)
            .add("client_secret", DropboxCredentials.CLIENT_SECRET)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build();

        Request request = new Request.Builder()
            .url("https://api.dropbox.com/oauth2/token")
            .post(requestBody)
            .build();

        Response response = httpClient.newCall(request).execute();
        JSONObject parsedResponse = new JSONObject(response.body().string());
        response.close();

        if (!response.isSuccessful()) return;

        accessToken = parsedResponse.getString("access_token");
    }

    public boolean isAuthenticated() {
        return !accessToken.isEmpty();
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

    public AuthenticationProvider getAuthProvider() {
        return AuthenticationProvider.DROPBOX;
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
}
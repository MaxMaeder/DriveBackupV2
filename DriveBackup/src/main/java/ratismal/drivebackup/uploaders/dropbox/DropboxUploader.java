package ratismal.drivebackup.uploaders.dropbox;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import ratismal.drivebackup.http.HttpClient;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.uploaders.AuthenticationProvider;
import ratismal.drivebackup.uploaders.Authenticator;
import ratismal.drivebackup.uploaders.Obfusticate;
import ratismal.drivebackup.uploaders.UploadLogger;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.uploaders.UploaderUtils;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.NetUtil;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public final class DropboxUploader extends Uploader {

    private static final String UPLOADER_NAME = "Dropbox";
    private static final String ID = "dropbox";

    /**
     * Global Dropbox tokens
     */
    private String accessToken = "";
    private String refreshToken;

    /**
     * Tests the Dropbox account by uploading a small file
     *  @param testFile the file to upload during the test
     */
    public void test(@NotNull java.io.File testFile) {
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(testFile.toPath()))) {
            byte[] content = new byte[(int) testFile.length()];
            dis.readFully(content);
            RequestBody requestBody = RequestBody.create(content, UploaderUtils.getOctetStreamMediaType());
            String destination = getLocalSaveDirectory();
            JSONObject dropboxJson = new JSONObject();
            dropboxJson.put("path", "/" + destination + "/" + testFile.getName());
            String dropboxArg = dropboxJson.toString();
            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Dropbox-API-Arg", dropboxArg)
                .url("https://content.dropboxapi.com/2/files/upload")
                .post(requestBody)
                .build();
            Response response = HttpClient.getHttpClient().newCall(request).execute();
            int statusCode = response.code();
            response.close();
            if (statusCode != 200) {
                setErrorOccurred(true);
            }
            TimeUnit.SECONDS.sleep(5L);
            JSONObject deleteJson = new JSONObject();
            deleteJson.put("path", "/" + destination + "/" + testFile.getName());
            RequestBody deleteRequestBody = RequestBody.create(deleteJson.toString(), UploaderUtils.getJsonMediaType());
            request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://api.dropboxapi.com/2/files/delete_v2")
                .post(deleteRequestBody)
                .build();
            response = HttpClient.getHttpClient().newCall(request).execute();
            statusCode = response.code();
            response.close();
            if (statusCode != 200) {
                setErrorOccurred(true);
            }
        } catch (IOException | InterruptedException exception) {
            NetUtil.catchException(exception, "api.dropboxapi.com", logger);
            MessageUtil.sendConsoleException(exception);
            setErrorOccurred(true);
        }
    }
    
    @Contract (pure = true)
    @Override
    public boolean isAuthenticated() {
        return !accessToken.isEmpty();
    }

    /**
     * Uploads the specified file to the authenticated user's Dropbox inside a
     * folder for the specified file type.
     *
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     */
    public void uploadFile(@NotNull java.io.File file, @NotNull String type) {
        String destination = getRemoteSaveDirectory();
        long fileSize = file.length();
        String folder = type.replaceAll("\\.{1,2}\\/", "");
        folder = folder.replace(".\\", "");
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(file.toPath()))) {
            if (fileSize > 150_000_000L /* 150MB */) {
                // Chunked upload
                // 10 MB chunk
                final int CHUNKED_UPLOAD_CHUNK_SIZE = (1024 * 1024 * 10);
                long uploaded = 0L;
                byte[] buff = new byte[CHUNKED_UPLOAD_CHUNK_SIZE];
                String sessionId = null;
                // (1) Start
                if (sessionId == null) {
                    dis.readFully(buff);
                    RequestBody requestBody = RequestBody.create(buff, UploaderUtils.getOctetStreamMediaType());
                    Request request = new Request.Builder()
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .post(requestBody)
                        .url("https://content.dropboxapi.com/2/files/upload_session/start")
                        .build();
                    Response response = HttpClient.getHttpClient().newCall(request).execute();
                    JSONObject parsedResponse = new JSONObject(response.body().string());
                    sessionId = parsedResponse.getString("session_id");
                    response.close();
                    uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;
                }
                // (2) Append
                while (fileSize - uploaded > CHUNKED_UPLOAD_CHUNK_SIZE) {
                    dis.readFully(buff);
                    RequestBody requestBody = RequestBody.create(buff, UploaderUtils.getOctetStreamMediaType());
                    JSONObject dropboxCursor = new JSONObject();
                    dropboxCursor.put("session_id", sessionId);
                    dropboxCursor.put("offset", uploaded);
                    JSONObject dropboxJson = new JSONObject();
                    dropboxJson.put("cursor", dropboxCursor);
                    String dropboxArg = dropboxJson.toString();
                    Request request = new Request.Builder()
                        .addHeader("Dropbox-API-Arg", dropboxArg)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .post(requestBody)
                        .url("https://content.dropboxapi.com/2/files/upload_session/append_v2")
                        .build();
                    Response response = HttpClient.getHttpClient().newCall(request).execute();
                    response.close();
                    uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;
                }
                // (3) Finish
                int remainingSize = (int) (fileSize - uploaded);
                byte[] remaining = new byte[remainingSize];
                dis.readFully(remaining);
                RequestBody requestBody = RequestBody.create(remaining, UploaderUtils.getOctetStreamMediaType());
                JSONObject dropboxCursor = new JSONObject();
                dropboxCursor.put("session_id", sessionId);
                dropboxCursor.put("offset", uploaded);
                JSONObject dropboxCommit = new JSONObject();
                dropboxCommit.put("path", "/" + destination + "/" + folder + "/" + file.getName());
                JSONObject dropboxJson = new JSONObject();
                dropboxJson.put("cursor", dropboxCursor);
                dropboxJson.put("commit", dropboxCommit);
                String dropboxArg = dropboxJson.toString();
                Request request = new Request.Builder()
                    .addHeader("Dropbox-API-Arg", dropboxArg)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .post(requestBody)
                    .url("https://content.dropboxapi.com/2/files/upload_session/finish")
                    .build();
                Response response = HttpClient.getHttpClient().newCall(request).execute();
                response.close();
            } else {
                // Single upload
                byte[] content = new byte[(int) fileSize];
                dis.readFully(content);
                RequestBody requestBody = RequestBody.create(content, UploaderUtils.getOctetStreamMediaType());
                JSONObject dropboxJson = new JSONObject();
                dropboxJson.put("path", "/" + destination + "/" + folder + "/" + file.getName());
                String dropboxArg = dropboxJson.toString();
                Request request = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Dropbox-API-Arg", dropboxArg)
                    .url("https://content.dropboxapi.com/2/files/upload")
                    .post(requestBody)
                    .build();
                Response response = HttpClient.getHttpClient().newCall(request).execute();
                response.close();
            }
            try {
                pruneBackups(folder);
            } catch (Exception e) {
                logger.log("backup-method-prune-failed");
                throw e;
            }
        } catch (Exception exception) {
            NetUtil.catchException(exception, "api.dropboxapi.com", logger);
            MessageUtil.sendConsoleException(exception);
            setErrorOccurred(true);
        }
    }

    /**
     * Deletes the oldest files past the number to retain from the FTP server inside
     * the specified folder for the file type.
     * <p>
     * The number of files to retain is specified by the user in the
     * {@code config.yml}
     * 
     * @param type the type of file (ex. plugins, world)
     * @throws Exception
     */
    private void pruneBackups(String type) throws Exception {
        type = type.replace("./", "");
        type = type.replace(".\\", "");
        int fileLimit = getKeepCount();
        if (fileLimit == -1) {
            return;
        }
        String destination = getRemoteSaveDirectory();
        TreeMap<Instant, String> files = getZipFiles(destination, type);
        if (files.size() > fileLimit) {
            Map<String, String> placeholders = new HashMap<>(3);
            placeholders.put("file-count", String.valueOf(files.size()));
            placeholders.put("upload-method", getName());
            placeholders.put("file-limit", String.valueOf(fileLimit));
            logger.info("backup-method-limit-reached", placeholders);
            while (files.size() > fileLimit) {
                JSONObject deleteJson = new JSONObject();
                deleteJson.put("path", "/" + destination + "/" + type + "/" + files.firstEntry().getValue());
                RequestBody deleteRequestBody = RequestBody.create(deleteJson.toString(), UploaderUtils.getJsonMediaType());
                Request deleteRequest = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .url("https://api.dropboxapi.com/2/files/delete_v2")
                    .post(deleteRequestBody)
                    .build();
                Response deleteResponse = HttpClient.getHttpClient().newCall(deleteRequest).execute();
                deleteResponse.close();
                files.remove(files.firstKey());
            }
        }
    }

    /**
     * Returns a list of ZIP files, and their modification dates inside the given folder.
     * @return a map of files, and their modification dates
     * @throws Exception
     */
    @NotNull
    private TreeMap<Instant, String> getZipFiles(String destination, String type) throws Exception {
        TreeMap<Instant, String> files = new TreeMap<>();
        JSONObject json = new JSONObject();
        json.put("path", "/" + destination + "/" + type);
        RequestBody requestBody = RequestBody.create(json.toString(), UploaderUtils.getJsonMediaType());
        Request request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://api.dropboxapi.com/2/files/list_folder")
            .post(requestBody)
            .build();
        Response response = HttpClient.getHttpClient().newCall(request).execute();
        JSONObject parsedResponse = new JSONObject(response.body().string());
        JSONArray resFiles = parsedResponse.getJSONArray("entries");
        response.close();
        for (int i = 0; i < resFiles.length(); i++) {
            JSONObject file = resFiles.getJSONObject(i);
            if (file.getString("name").endsWith(".zip")) {
                files.put(Instant.parse(file.getString("server_modified")), file.getString("name"));
            }
        }
        return files;
    }

    /**
     * Creates an instance of the {@code DropboxUploader} object
     */
    public DropboxUploader(DriveBackupInstance instance, UploadLogger logger) {
        super(instance, UPLOADER_NAME, ID, AuthenticationProvider.DROPBOX, logger);
        refreshToken = Authenticator.getRefreshToken(AuthenticationProvider.DROPBOX);
        try {
            retrieveNewAccessToken();
        } catch (Exception e) {
            instance.getLoggingHandler().error("Failed to retrieve new access token from Dropbox", e);
            setErrorOccurred(true);
        }
    }

    /**
     * Gets a new Dropbox access token for the authenticated user
     */
    private void retrieveNewAccessToken() throws Exception {
        RequestBody requestBody = new FormBody.Builder()
            .add("client_id", Obfusticate.decrypt(AuthenticationProvider.DROPBOX.getClientId()))
            .add("client_secret", Obfusticate.decrypt(AuthenticationProvider.DROPBOX.getClientSecret()))
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build();
        Request request = new Request.Builder()
            .url("https://api.dropbox.com/oauth2/token")
            .post(requestBody)
            .build();
        Response response = HttpClient.getHttpClient().newCall(request).execute();
        ResponseBody body = response.body();
        JSONObject parsedResponse = new JSONObject(body.string());
        response.close();
        if (!response.isSuccessful()) {
            return;
        }
        accessToken = parsedResponse.getString("access_token");
    }

    /**
     * Closes any remaining connections retrieveNewAccessToken
     */
    @Contract (pure = true)
    public void close() {
        // nothing needs to be done
    }
}

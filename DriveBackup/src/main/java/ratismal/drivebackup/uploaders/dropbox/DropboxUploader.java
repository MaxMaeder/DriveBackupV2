package ratismal.drivebackup.uploaders.dropbox;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import ratismal.drivebackup.UploadThread.UploadLogger;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.uploaders.Authenticator;
import ratismal.drivebackup.uploaders.Authenticator.AuthenticationProvider;
import ratismal.drivebackup.uploaders.Obfusticate;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.NetUtil;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static ratismal.drivebackup.config.Localization.intl;

public class DropboxUploader extends Uploader {

    public static final String UPLOADER_NAME = "Dropbox";

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
            Response response = DriveBackup.httpClient.newCall(request).execute();
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
            response = DriveBackup.httpClient.newCall(request).execute();
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
    public void uploadFile(@NotNull final java.io.File file, @NotNull final String type) {
        String destination = ConfigParser.getConfig().backupStorage.remoteDirectory;
        long fileSize = file.length();
        MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");
        String folder = type.replaceAll("\\.{1,2}\\/", "");
        folder = folder.replace(".\\", "");
        try (DataInputStream dis = new DataInputStream(Files.newInputStream(file.toPath()))) {
            if (fileSize > 150000000L /* 150MB */) {
                // Chunked upload
                // 10 MB chunk
                final int CHUNKED_UPLOAD_CHUNK_SIZE = (1024 * 1024 * 10);
                long uploaded = 0L;
                byte[] buff = new byte[CHUNKED_UPLOAD_CHUNK_SIZE];
                String sessionId = null;
                // (1) Start
                if (sessionId == null) {
                    dis.readFully(buff);
                    RequestBody requestBody = RequestBody.create(buff, OCTET_STREAM);
                    Request request = new Request.Builder()
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .post(requestBody)
                        .url("https://content.dropboxapi.com/2/files/upload_session/start")
                        .build();
                    Response response = DriveBackup.httpClient.newCall(request).execute();
                    JSONObject parsedResponse = new JSONObject(response.body().string());
                    sessionId = parsedResponse.getString("session_id");
                    response.close();
                    uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;
                }
                // (2) Append
                while (fileSize - uploaded > CHUNKED_UPLOAD_CHUNK_SIZE) {
                    dis.readFully(buff);
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
                    Response response = DriveBackup.httpClient.newCall(request).execute();
                    response.close();
                    uploaded += CHUNKED_UPLOAD_CHUNK_SIZE;
                }
                // (3) Finish
                int remainingSize = (int) (fileSize - uploaded);
                byte[] remaining = new byte[remainingSize];
                dis.readFully(remaining);
                RequestBody requestBody = RequestBody.create(remaining, OCTET_STREAM);
                JSONObject dropboxCursor = new JSONObject();
                dropboxCursor.put("session_id", sessionId);
                dropboxCursor.put("offset", uploaded);
                JSONObject dropboxCommit = new JSONObject();
                dropboxCommit.put("path", "/" + destination + "/" + folder + "/" + file.getName());
                JSONObject dropboxJson = new JSONObject();
                dropboxJson.put("cursor", dropboxCursor);
                dropboxJson.put("commit", dropboxCommit);
                String dropbox_arg = dropboxJson.toString();
                Request request = new Request.Builder()
                    .addHeader("Dropbox-API-Arg", dropbox_arg)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .post(requestBody)
                    .url("https://content.dropboxapi.com/2/files/upload_session/finish")
                    .build();
                Response response = DriveBackup.httpClient.newCall(request).execute();
                response.close();
            } else {
                // Single upload
                byte[] content = new byte[ (int) fileSize];
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
                Response response = DriveBackup.httpClient.newCall(request).execute();
                response.close();
            }
            try {
                pruneBackups(folder);
            } catch (Exception e) {
                logger.log(intl("backup-method-prune-failed"));
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
        Config config = ConfigParser.getConfig();
        type = type.replace("./", "");
        type = type.replace(".\\", "");
        String destination = config.backupStorage.remoteDirectory;
        int fileLimit = config.backupStorage.keepCount;
        if (fileLimit == -1) {
            return;
        }
        TreeMap<Instant, String> files = getZipFiles(destination, type);
        if (files.size() > fileLimit) {
            logger.info(intl("backup-method-limit-reached"),
                "file-count", String.valueOf(files.size()),
                "upload-method", getName(),
                "file-limit", String.valueOf(fileLimit));
            while (files.size() > fileLimit) {
                JSONObject deleteJson = new JSONObject();
                deleteJson.put("path", "/" + destination + "/" + type + "/" + files.firstEntry().getValue());
                RequestBody deleteRequestBody = RequestBody.create(deleteJson.toString(),
                                                        MediaType.parse("application/json"));
                Request deleteRequest = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .url("https://api.dropboxapi.com/2/files/delete_v2")
                    .post(deleteRequestBody)
                    .build();
                Response deleteResponse = DriveBackup.httpClient.newCall(deleteRequest).execute();
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
        RequestBody requestBody = RequestBody.create(json.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://api.dropboxapi.com/2/files/list_folder")
            .post(requestBody)
            .build();
        Response response = DriveBackup.httpClient.newCall(request).execute();
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
    public DropboxUploader(UploadLogger logger) {
        super(UPLOADER_NAME, "dropbox");
        setAuthProvider(AuthenticationProvider.DROPBOX);
        this.logger = logger;
        try {
            refreshToken = Authenticator.getRefreshToken(AuthenticationProvider.DROPBOX);
            retrieveNewAccessToken();
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
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
        Response response = DriveBackup.httpClient.newCall(request).execute();
        JSONObject parsedResponse = new JSONObject(response.body().string());
        response.close();
        if (!response.isSuccessful()) {
            return;
        }
        accessToken = parsedResponse.getString("access_token");
    }

    /**
     * Closes any remaining connections retrieveNewAccessToken
     */
    public void close() {
        // nothing needs to be done
    }
}

package ratismal.drivebackup.uploaders.onedrive;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import ratismal.drivebackup.UploadThread.UploadLogger;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.uploaders.Authenticator;
import ratismal.drivebackup.uploaders.Authenticator.AuthenticationProvider;
import ratismal.drivebackup.uploaders.Obfusticate;
import ratismal.drivebackup.uploaders.Uploader;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.NetUtil;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static ratismal.drivebackup.config.Localization.intl;

/**
 * Created by Redemption on 2/24/2016.
 */
public class OneDriveUploader extends Uploader {
    public static final int EXPONENTIAL_BACKOFF_MILLIS_DEFAULT = 1000;
    public static final int EXPONENTIAL_BACKOFF_FACTOR = 5;
    public static final int MAX_RETRY_ATTEMPTS = 3;

    private UploadLogger logger;
    
    private long totalUploaded;
    private long lastUploaded;
    private String accessToken = "";
    private String refreshToken;

    public static final String UPLOADER_NAME = "OneDrive";

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
     * Creates an instance of the {@code OneDriveUploader} object
     */
    public OneDriveUploader(UploadLogger logger) {
        super(UPLOADER_NAME, "onedrive");
        this.logger = logger;
        setAuthProvider(AuthenticationProvider.ONEDRIVE);
        try {
            refreshToken = Authenticator.getRefreshToken(AuthenticationProvider.ONEDRIVE);
            retrieveNewAccessToken();
            setRanges(new String[0]);
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Gets a new OneDrive access token for the authenticated user
     */
    private void retrieveNewAccessToken() throws Exception {
        RequestBody requestBody = new FormBody.Builder()
            .add("client_id", Obfusticate.decrypt(AuthenticationProvider.ONEDRIVE.getClientId()))
            .add("scope", "offline_access Files.ReadWrite")
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .add("redirect_uri", "https://login.microsoftonline.com/common/oauth2/nativeclient")
            .build();
        Request request = new Request.Builder()
            .url("https://login.microsoftonline.com/common/oauth2/v2.0/token")
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
    @Override
    public boolean isAuthenticated() {
        return !accessToken.isEmpty();
    }

    /**
     * Tests the OneDrive account by uploading a small file
     *  @param testFile the file to upload during the test
     */
    @Override
    public void test(java.io.File testFile) {
        try {
            String destination = normalizePath(ConfigParser.getConfig().backupStorage.remoteDirectory);
            FQID destinationId = createPath(destination);
            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + destination + "/" + testFile.getName() + ":/content")
                .put(RequestBody.create(testFile, MediaType.parse("plain/txt")))
                .build();
            Response response = DriveBackup.httpClient.newCall(request).execute();
            int statusCode = response.code();
            response.close();
            if (statusCode != 201) {
                setErrorOccurred(true);
            }
            TimeUnit.SECONDS.sleep(5);
            request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + destination + "/" + testFile.getName() + ":/")
                .delete() // TODO delete permanently
                .build();
            response = DriveBackup.httpClient.newCall(request).execute();
            statusCode = response.code();
            response.close();
            if (statusCode != 204) {
                setErrorOccurred(true);
            }
        } catch (Exception exception) {
            NetUtil.catchException(exception, "graph.microsoft.com", logger);
            MessageUtil.sendConsoleException(exception);
            setErrorOccurred(true);
        }
    }

    /**
     * Uploads the specified file to the authenticated user's OneDrive inside a folder for the specified file location.
     * @param file the file
     * @param location of the file (ex. plugins, world)
     */
    @Override
    public void uploadFile(java.io.File file, String location) throws IOException {
        try {
            resetRanges();
            String destinationRoot = normalizePath(ConfigParser.getConfig().backupStorage.remoteDirectory);
            String destinationPath = destinationRoot + '/' + location;
            FQID destinationId = createPath(destinationPath);
            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + destinationPath + "/" + file.getName() + ":/createUploadSession")
                .post(RequestBody.create("{}", jsonMediaType))
                .build();
            JSONObject parsedResponse;
            try (Response response = DriveBackup.httpClient.newCall(request).execute()) {
                parsedResponse = new JSONObject(response.body().string());
            }
            String uploadURL = parsedResponse.getString("uploadUrl");
            raf = new RandomAccessFile(file, "r");
            int exponentialBackoffMillis = EXPONENTIAL_BACKOFF_MILLIS_DEFAULT;
            int retryCount = 0;
            while (true) {
                byte[] bytesToUpload = getChunk();
                request = new Request.Builder()
                    .addHeader("Content-Range", String.format("bytes %d-%d/%d", getTotalUploaded(), getTotalUploaded() + bytesToUpload.length - 1, file.length()))
                    .url(uploadURL)
                    .put(RequestBody.create(bytesToUpload, zipMediaType))
                    .build();
                try (Response uploadResponse = DriveBackup.httpClient.newCall(request).execute()) {
                    if (uploadResponse.code() == 202) {
                        parsedResponse = new JSONObject(uploadResponse.body().string());
                        List<Object> nextExpectedRanges = parsedResponse.getJSONArray("nextExpectedRanges").toList();
                        setRanges(nextExpectedRanges.toArray(new String[0]));
                        exponentialBackoffMillis = EXPONENTIAL_BACKOFF_MILLIS_DEFAULT;
                        retryCount = 0;
                    } else if (uploadResponse.code() == 201 || uploadResponse.code() == 200) {
                        break;
                    } else { // conflict after successful upload not handled
                        if (retryCount > MAX_RETRY_ATTEMPTS) {
                            request = new Request.Builder().url(uploadURL).delete().build();
                            DriveBackup.httpClient.newCall(request).execute().close();
                            throw new IOException(String.format("Upload failed after %d retries. %d %s", MAX_RETRY_ATTEMPTS, uploadResponse.code(), uploadResponse.message()));
                        }
                        if (uploadResponse.code() >= 500 && uploadResponse.code() < 600) {
                            Thread.sleep(exponentialBackoffMillis);
                            exponentialBackoffMillis *= EXPONENTIAL_BACKOFF_FACTOR;
                        }
                        retryCount++;
                    }
                }
            }
            try {
                pruneBackups(destinationPath);
            } catch (Exception e) {
                logger.log(intl("backup-method-prune-failed"));
                throw e;
            }
        } catch (Exception exception) {
            NetUtil.catchException(exception, "graph.microsoft.com", logger);
            MessageUtil.sendConsoleException(exception);
            setErrorOccurred(true);
        }
        raf.close();
    }

    /**
    * Closes any remaining connections retrieveNewAccessToken
    */
    public void close() {
        // nothing needs to be done
    }

    /**
     * fully qualified item id
     */
    private static class FQID {
        public final String driveId;
        public final String itemId;

        public FQID(String driveId, String itemId) {
            this.driveId = driveId;
            this.itemId = itemId;
        }
    }

    /**
     * removes "." and ".." segments from the path
     * @param path to normalize
     * @return the normalized path
     */
    @NotNull
    private String normalizePath(String path) {
        StringBuilder normalized = new StringBuilder();
        for (String part : path.split("[/\\\\]")) {
            if (".".equals(part) || "..".equals(part)) {
                continue;
            }
            normalized.append('/').append(part);
        }
        return normalized.substring(1);
    }

    /**
     * creates all folders in the path if they don't already exist
     * @param path to create the folders for
     * @return FQID of the last folder in the path
     * @throws IOException if the folder could not be created;
     * or if the api request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    @NotNull
    private FQID createPath(String path) throws IOException {
        Iterator<String> parts = Arrays.stream(path.split("/")).iterator();
        FQID root = createRootFolder(parts.next());
        for (; parts.hasNext(); ) {
            String folder = parts.next();
            root = createFolder(root, folder);
        }
        return root;
    }

    /**
     * creates a folder at the root if it doesn't already exist
     * @param root of where to create the folder
     * @param folder name to create
     * @return FQID of the folder
     * @throws IOException if the folder could not be created;
     * or if the api request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    @NotNull
    private FQID createFolder(FQID root, String folder) throws IOException {
        FQID item = getFolder(root, folder);
        if (item != null) {
            return item;
        }
        RequestBody requestBody = RequestBody.create("{ \"name\": \"" + folder
                + "\", \"folder\": {}, \"@microsoft.graph.conflictBehavior\": \"fail\" }", jsonMediaType);
        Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/me/drive/root/children")
                .post(requestBody)
                .build();
        try (Response response = DriveBackup.httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Couldn't create folder " + folder);
            }
            JSONObject parsedResponse = new JSONObject(response.body().string());
            String driveId = parsedResponse.getJSONObject("parentReference").getString("driveId");
            String itemId = parsedResponse.getString("id");
            return new FQID(driveId, itemId);
        }
    }

    /**
     * creates a folder at the drive root if it doesn't already exist
     * @param folder name to create
     * @return FQID of the folder
     * @throws IOException if the folder could not be created;
     * or if the api request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    @NotNull
    private FQID createRootFolder(String folder) throws IOException {
        FQID item = getRootFolder(folder);
        if (item != null) {
            return item;
        }
        RequestBody requestBody = RequestBody.create("{ \"name\": \""
                + folder + "\", \"folder\": {}, \"@name.conflictBehavior\": \"fail\" }", jsonMediaType);
        Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/me/drive/root/children")
                .post(requestBody)
                .build();
        try (Response response = DriveBackup.httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Couldn't create folder " + folder);
            }
            JSONObject parsedResponse = new JSONObject(response.body().string());
            String driveId = parsedResponse.getJSONObject("parentReference").getString("driveId");
            String itemId = parsedResponse.getString("id");
            return new FQID(driveId, itemId);
        }
    }

    /**
     * tries to find folder in the drive root
     * @param folder to search
     * @return FQID or null if not found
     */
    @Nullable
    private FQID getRootFolder(String folder) {
        try {
            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + folder + "?$select=id,parentReference,remoteItem")
                .build();
            JSONObject parsedResponse;
            try (Response response = DriveBackup.httpClient.newCall(request).execute()) {
                parsedResponse = new JSONObject(response.body().string());
            }
            if (parsedResponse.has("remoteItem")) {
               parsedResponse = parsedResponse.optJSONObject("remoteItem");
            }
            String driveId = parsedResponse.getJSONObject("parentReference").getString("driveId");
            String itemId = parsedResponse.getString("id");
            return new FQID(driveId, itemId);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * tries to find a folder under root
     * @param root to search
     * @param folder to look for
     * @return FQID or null if not found
     */
    @Nullable
    private FQID getFolder(FQID root, String folder) {
        try {
            Request request = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .url("https://graph.microsoft.com/v1.0/me/drives/" + root.driveId + "/items/" + root.itemId + "/children")
                    .build();
            JSONObject parsedResponse;
            try (Response response = DriveBackup.httpClient.newCall(request).execute()) {
                parsedResponse = new JSONObject(response.body().string());
            }
            JSONArray children = parsedResponse.getJSONArray("value");
            for (int i = 0; i < children.length(); i++) {
                JSONObject childItem = children.getJSONObject(i);
                String folderName = childItem.getString("name");
                if (folder.equals(folderName)) {
                    if (childItem.has("remoteItem")) {
                        childItem = childItem.optJSONObject("remoteItem");
                    }
                    String driveId = childItem.getJSONObject("parentReference").getString("driveId");
                    String itemId = childItem.getString("id");
                    return new FQID(driveId, itemId);
                }
            }
            // TODO handle @odata.nextLink
        } catch (Exception exception) {
            return null;
        }
        return null;
    }

    /**
     * Deletes the oldest files in the specified folder past the number to retain from the authenticated user's OneDrive.
     * <p>
     * The number of files to retain is specified by the user in the {@code config.yml}
     * @param parent the folder containing the files
     * @throws Exception
     */
    private void pruneBackups(String parent) throws Exception {
        int fileLimit = ConfigParser.getConfig().backupStorage.keepCount;
        if (fileLimit == -1) {
            return;
        }
        Request request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + parent + ":/children?sort_by=createdDateTime")
            .build();
        Response response = DriveBackup.httpClient.newCall(request).execute();
        JSONObject parsedResponse = new JSONObject(response.body().string());
        response.close();
        ArrayList<String> fileIDs = new ArrayList<>();
        JSONArray jsonArray = parsedResponse.getJSONArray("value");
        for (int i = 0; i < jsonArray.length(); i++) {
            fileIDs.add(jsonArray.getJSONObject(i).getString("id"));
        }
        if(fileLimit < fileIDs.size()){
            logger.info(
                intl("backup-method-limit-reached"), 
                "file-count", String.valueOf(fileIDs.size()),
                "upload-method", getName(),
                "file-limit", String.valueOf(fileLimit));
        }
        for (Iterator<String> iterator = fileIDs.listIterator(); iterator.hasNext(); ) {
            String fileIDValue = iterator.next();
            if (fileLimit < fileIDs.size()) {
                request = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .url("https://graph.microsoft.com/v1.0/me/drive/items/" + fileIDValue)
                    .delete()
                    .build();
                DriveBackup.httpClient.newCall(request).execute().close();
                iterator.remove();
            }
            if (fileIDs.size() <= fileLimit){
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
     * Resets the number of bytes uploaded in the last chunk, and the number of bytes uploaded in total.
     */
    private void resetRanges() {
        lastUploaded = 0;
        totalUploaded = 0;
    }
    
    /**
     * Sets the number of bytes uploaded in the last chunk,
     * and the number of bytes uploaded in total from the ranges of bytes the OneDrive API requested to be uploaded last.
     * @param stringRanges the ranges of bytes requested
     */
    private void setRanges(@NotNull String[] stringRanges) {
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
            totalUploaded = ranges[0].start;
        }
    }

    /**
     * Gets an array of bytes to upload next from the file buffer based on the number of bytes uploaded so far.
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
     * Gets the number of bytes uploaded in total
     * @return the number of bytes
     */
    private long getTotalUploaded() {
        return totalUploaded;
    }
}

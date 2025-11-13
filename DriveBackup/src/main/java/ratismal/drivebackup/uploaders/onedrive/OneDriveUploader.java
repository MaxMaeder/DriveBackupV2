package ratismal.drivebackup.uploaders.onedrive;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static ratismal.drivebackup.config.Localization.intl;

/**
 * Created by Redemption on 2/24/2016.
 */
public class OneDriveUploader extends Uploader {
    private static final int EXPONENTIAL_BACKOFF_MILLIS_DEFAULT = 1000;
    private static final int EXPONENTIAL_BACKOFF_FACTOR = 5;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private String accessToken = "";
    private String refreshToken;

    public static final String UPLOADER_NAME = "OneDrive";

    private static final MediaType zipMediaType = MediaType.parse("application/zip; charset=utf-8");
    private static final MediaType jsonMediaType = MediaType.parse("application/json; charset=utf-8");
    private static final MediaType textMediaType = MediaType.parse("text/plain");

    // as per ms docs should be multiple of 320 KiB (327'680 bytes)
    private static final int UPLOAD_CHUNK_SIZE = 5 * 1024 * 1024;

    /**
     * Creates an instance of the {@code OneDriveUploader} object
     */
    public OneDriveUploader(UploadLogger logger) {
        super(UPLOADER_NAME, "onedrive");
        this.logger = logger;
        setAuthProvider(AuthenticationProvider.ONEDRIVE);
        try {
            refreshToken = Authenticator.getRefreshToken(getAuthProvider());
            retrieveNewAccessToken();
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
            setErrorOccurred(true);
        }
    }

    /**
     * Gets a new OneDrive access token for the authenticated user
     * @throws Exception if the clientId could not be decrypted
     * @throws IOException if the request could not be executed, or was not successful
     * @throws JSONException if the response does not contain the expected values
     */
    private void retrieveNewAccessToken() throws Exception {
        RequestBody requestBody = new FormBody.Builder()
            .add("client_id", Obfusticate.decrypt(getAuthProvider().getClientId()))
            .add("scope", "offline_access Files.ReadWrite")
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .add("redirect_uri", "https://login.microsoftonline.com/common/oauth2/nativeclient")
            .build();
        Request request = new Request.Builder()
            .url("https://login.microsoftonline.com/common/oauth2/v2.0/token")
            .post(requestBody)
            .build();
        try (Response response = DriveBackup.httpClient.newCall(request).execute()) {
            //noinspection DataFlowIssue (response.body() is non-null after Call.execute())
            JSONObject parsedResponse = new JSONObject(response.body().string());
            if (!response.isSuccessful()) {
                String error = parsedResponse.optString("error");
                String description = parsedResponse.optString("error_description");
                throw new IOException(String.format("%s : %s", error, description));
            }
            accessToken = parsedResponse.getString("access_token");
            refreshToken = parsedResponse.getString("refresh_token");
        }
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
    public void test(File testFile) {
        try {
            String destination = normalizePath(ConfigParser.getConfig().backupStorage.remoteDirectory);
            FQID destinationId = createPath(destination);
            FQID testFileId = uploadSmallFile(testFile, destinationId);
            TimeUnit.SECONDS.sleep(5);
            recycleItem(testFileId.driveId, testFileId.itemId);
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
    public void uploadFile(File file, String location) {
        try {
            retrieveNewAccessToken();
            String destinationRoot = normalizePath(ConfigParser.getConfig().backupStorage.remoteDirectory);
            String destinationPath = concatPath(destinationRoot, normalizePath(location));
            FQID destinationId = createPath(destinationPath);
            String uploadURL = createUploadSession(file.getName(), destinationId);
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                uploadToSession(uploadURL, raf);
            }
            try {
                pruneBackups(destinationId);
            } catch (Exception e) {
                logger.log(intl("backup-method-prune-failed"));
                throw e;
            }
        }
        catch (Exception exception) {
            NetUtil.catchException(exception, "graph.microsoft.com", logger);
            MessageUtil.sendConsoleException(exception);
            setErrorOccurred(true);
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
    * Closes any remaining connections retrieveNewAccessToken
    */
    public void close() {
        try {
            Authenticator.saveRefreshToken(getAuthProvider(), refreshToken);
        } catch (Exception e) {
            MessageUtil.sendConsoleException(e);
        }
    }

    /**
     * fully qualified item id
     */
    private static class FQID {
        public final String driveId;
        public final String itemId;

        public FQID(@NotNull String driveId, @NotNull String itemId) {
            this.driveId = driveId;
            this.itemId = itemId;
        }
    }

    /**
     * removes "." and ".." segments from the path,
     * replaces all separators with '/',
     * discards the first leading and trailing separator
     * @param path to normalize
     * @return the normalized relative path
     */
    @NotNull
    private static String normalizePath(@NotNull String path) {
        StringBuilder normalized = new StringBuilder();
        for (String part : path.split("[/\\\\]")) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                continue;
            }
            normalized.append('/').append(part);
        }
        return normalized.substring(Math.min(normalized.length(), 1));
    }

    /**
     * joins the two paths with '/' while handling emptiness of either side
     * @param lhs left hand side of to be joined path
     * @param rhs right hand side of to be joined path
     * @return joined path
     */
    @NotNull
    private static String concatPath(@NotNull String lhs, @NotNull String rhs) {
        if (rhs.isEmpty()) {
            return lhs;
        }
        if (lhs.isEmpty()) {
            return rhs;
        }
        if(lhs.endsWith("/")) {
            lhs = lhs.substring(0, lhs.length() - 1);
        }
        if(rhs.startsWith("/")) {
            rhs = rhs.substring(1);
        }
        return lhs + '/' + rhs;
    }

    /**
     * creates all folders in the path if they don't already exist
     * @param path to create the folders for
     * @return {@link OneDriveUploader.FQID FQID} of the last folder in the path
     * @throws IOException if the request could not be executed
     * @throws GraphApiErrorException if the folder could not be found or created
     * @throws JSONException if the response does not contain the expected items
     */
    @NotNull
    private FQID createPath(@NotNull String path) throws IOException, GraphApiErrorException {
        Iterator<String> parts = Arrays.stream(path.split("/")).iterator();
        FQID root = createRootFolder(parts.next());
        while (parts.hasNext()) {
            String folder = parts.next();
            root = createFolder(root, folder);
        }
        return root;
    }

    /**
     * creates a folder at the root if it doesn't already exist
     * @param root of where to create the folder
     * @param folder name to create
     * @return {@link OneDriveUploader.FQID FQID} of the folder
     * @throws IOException if the request could not be executed
     * @throws GraphApiErrorException if the folder could not be found or created
     * @throws JSONException if the response does not contain the expected items
     */
    @NotNull
    private FQID createFolder(@NotNull FQID root, @NotNull String folder) throws IOException, GraphApiErrorException {
        FQID item = getFolder(root, folder);
        if (item != null) {
            return item;
        }
        RequestBody requestBody = RequestBody.create("{ \"name\": \"" + folder
            + "\", \"folder\": {}, \"@microsoft.graph.conflictBehavior\": \"fail\" }", jsonMediaType);
        Request request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://graph.microsoft.com/v1.0/drives/" + root.driveId + "/items/" + root.itemId + "/children")
            .post(requestBody)
            .build();
        try (Response response = DriveBackup.httpClient.newCall(request).execute()) {
            //noinspection DataFlowIssue (response.body() is non-null after Call.execute())
            String jsonResponse = response.body().string();
            if (response.code() != 201) {
                throw new GraphApiErrorException(response.code(), jsonResponse);
            }
            JSONObject parsedResponse = new JSONObject(jsonResponse);
            String driveId = parsedResponse.getJSONObject("parentReference").getString("driveId");
            String itemId = parsedResponse.getString("id");
            return new FQID(driveId, itemId);
        }
    }

    /**
     * creates a folder at the drive root if it doesn't already exist
     * @param folder name to create
     * @return {@link OneDriveUploader.FQID FQID} of the folder
     * @throws IOException if the request could not be executed
     * @throws GraphApiErrorException if the root could not be found or created
     * @throws JSONException if the response does not contain the expected items
     */
    @NotNull
    private FQID createRootFolder(@NotNull String folder) throws IOException, GraphApiErrorException {
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
            //noinspection DataFlowIssue (response.body() is non-null after Call.execute())
            String responseBody = response.body().string();
            if (response.code() != 201) {
                throw new GraphApiErrorException(response.code(), responseBody);
            }
            JSONObject parsedResponse = new JSONObject(responseBody);
            String driveId = parsedResponse.getJSONObject("parentReference").getString("driveId");
            String itemId = parsedResponse.getString("id");
            return new FQID(driveId, itemId);
        }
    }

    /**
     * tries to find folder in the drive root
     * @param folder to search
     * @return {@link OneDriveUploader.FQID FQID} or null if not found
     * @throws IOException if the request could not be executed
     * @throws GraphApiErrorException if the root could not be retrieved
     * @throws JSONException if the response does not contain the expected items
     */
    @Nullable
    private FQID getRootFolder(@NotNull String folder) throws IOException, GraphApiErrorException {
        String folderUrl = folder.isEmpty() ? folder : ":/" + folder;
        Request request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://graph.microsoft.com/v1.0/me/drive/root" + folderUrl + "?$select=id,parentReference,remoteItem")
            .build();
        JSONObject parsedResponse;
        try (Response response = DriveBackup.httpClient.newCall(request).execute()) {
            //noinspection DataFlowIssue (response.body() is non-null after Call.execute())
            String responseBody = response.body().string();
            if (response.code() == 404) {
                return null;
            }
            if (!response.isSuccessful()) {
                throw new GraphApiErrorException(response.code(), responseBody);
            }
            parsedResponse = new JSONObject(responseBody);
        }
        if (parsedResponse.has("remoteItem")) {
           parsedResponse = parsedResponse.getJSONObject("remoteItem");
        }
        String driveId = parsedResponse.getJSONObject("parentReference").getString("driveId");
        String itemId = parsedResponse.getString("id");
        return new FQID(driveId, itemId);
    }

    /**
     * tries to find a folder under root
     * @param root to search
     * @param folder to look for
     * @return {@link OneDriveUploader.FQID FQID} or null if not found
     * @throws IOException if the request could not be executed
     * @throws GraphApiErrorException if the children could not be retrieved
     * @throws JSONException if the response does not contain the expected items
     */
    @Nullable
    private FQID getFolder(@NotNull FQID root, @NotNull String folder) throws IOException, GraphApiErrorException {
        String queryParams = "?select=name,id,folder,parentReference,remoteItem";
        for (JSONObject childItem : getChildren(root, queryParams)) {
            String itemName = childItem.getString("name");
            if (folder.equals(itemName)) {
                if (!childItem.has("folder")) {
                    return null;
                }
                if (childItem.has("remoteItem")) {
                    childItem = childItem.getJSONObject("remoteItem");
                }
                String driveId = childItem.getJSONObject("parentReference").getString("driveId");
                String itemId = childItem.getString("id");
                return new FQID(driveId, itemId);
            }
        }
        return null;
    }

    /**
     * gets all children for a given folder
     * @param folder to query
     * @param queryParams line like "?$select=name"
     * @throws IOException on request execution failure
     * @throws GraphApiErrorException if the children could not be retrieved
     * @throws JSONException if the response does not contain the expected items
     */
    @NotNull
    private List<JSONObject> getChildren(@NotNull FQID folder, @NotNull String queryParams) throws IOException, GraphApiErrorException {
        ArrayList<JSONObject> allChildren = new ArrayList<>();
        String targetUrl = "https://graph.microsoft.com/v1.0/drives/" + folder.driveId
                + "/items/" + folder.itemId + "/children" + queryParams;
        while (true) {
            Request request = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .url(targetUrl)
                    .build();
            try (Response response = DriveBackup.httpClient.newCall(request).execute()) {
                //noinspection DataFlowIssue (response.body() is non-null after Call.execute())
                String responseBody = response.body().string();
                if (!response.isSuccessful()) {
                    throw new GraphApiErrorException(response.code(), responseBody);
                }
                JSONObject parsedResponse = new JSONObject(responseBody);
                JSONArray someChildren = parsedResponse.getJSONArray("value");
                allChildren.ensureCapacity(parsedResponse.optInt("@odata.count", someChildren.length()));
                for (int i = 0; i < someChildren.length(); i++) {
                    allChildren.add(someChildren.getJSONObject(i));
                }
                if (!parsedResponse.has("@odata.nextLink")) {
                    return allChildren;
                }
                targetUrl = parsedResponse.getString("@odata.nextLink");
            }
        }
    }

    /**
     * moves an item to the recycle bin
     *
     * @param driveId the ID of the drive of the item
     * @param itemId the ID of the item to be deleted
     * @throws IOException if the request could not be executed
     * @throws GraphApiErrorException if the item was not recycled
     */
    private void recycleItem(@NotNull String driveId, @NotNull String itemId) throws IOException, GraphApiErrorException {
        Request delteRequest = new Request.Builder()
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://graph.microsoft.com/v1.0/drives/" + driveId + "/items/" + itemId)
            .delete()
            .build();
        try (Response response = DriveBackup.httpClient.newCall(delteRequest).execute()) {
            if (response.code() != 204 && response.code() != 404) {
                //noinspection DataFlowIssue (response.body() is non-null after Call.execute())
                throw new GraphApiErrorException(response.code(), response.body().string());
            }
        }
    }

    /**
     * upload a file up to 250MB in size
     * @param file to upload
     * @param destinationFolder to upload the file into
     * @return {@link OneDriveUploader.FQID FQID} of the uploaded file
     * @throws IOException if the request could not be executed
     * @throws GraphApiErrorException if the file could not be uploaded
     */
    @NotNull
    private FQID uploadSmallFile(@NotNull File file, @NotNull FQID destinationFolder) throws IOException, GraphApiErrorException {
        Request uploadRequest = new Request.Builder()
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://graph.microsoft.com/v1.0/drives/" + destinationFolder.driveId + "/items/" + destinationFolder.itemId
                + ":/" + file.getName() + ":/content")
            .put(RequestBody.create(file, textMediaType))
            .build();
        try (Response response = DriveBackup.httpClient.newCall(uploadRequest).execute()) {
            //noinspection DataFlowIssue (response.body() is non-null after Call.execute())
            String responseBody = response.body().string();
            if (response.code() != 201) {
                throw new GraphApiErrorException(response.code(), responseBody);
            }
            JSONObject parsedResponse = new JSONObject(responseBody);
            return new FQID(destinationFolder.driveId, parsedResponse.getString("id"));
        }
    }

    /**
     * creates an upload session for a file in a destination folder on OneDrive.
     *
     * @param fileName of the file to upload
     * @param destinationFolder as {@link OneDriveUploader.FQID FQID}
     * @return {@link String} with the upload URL for the file
     * @throws IOException if there is an error executing the request
     * @throws GraphApiErrorException if the upload session was not created
     * @throws JSONException if the response does not contain the expected values
     */
    @NotNull
    private String createUploadSession(@NotNull String fileName, @NotNull FQID destinationFolder) throws IOException, GraphApiErrorException {
        Request request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://graph.microsoft.com/v1.0/drives/" + destinationFolder.driveId
                + "/items/" + destinationFolder.itemId + ":/" + fileName + ":/createUploadSession")
            .post(RequestBody.create("{}", jsonMediaType))
            .build();
        try (Response response = DriveBackup.httpClient.newCall(request).execute()) {
            //noinspection DataFlowIssue (response.body() is non-null after Call.execute())
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new GraphApiErrorException(response.code(), responseBody);
            }
            return new JSONObject(responseBody).getString("uploadUrl");
        }
    }

    /**
     * uploads the file to a session with the given upload URL. some errors are handled via automatic retries.
     *
     * @param uploadURL of the upload session
     * @param randomAccessFile to upload
     * @throws IOException if a request could not be executed, or randomAccessFile could not be read
     * @throws GraphApiErrorException with the last error after max retries
     * @throws InterruptedException if interrupted during retries
     * @throws JSONException if the responses do not have the expected values
     * @throws NumberFormatException if the responses do not have the expected values
     * @throws IndexOutOfBoundsException if the responses do not have the expected values
     */
    private void uploadToSession(@NotNull String uploadURL, @NotNull RandomAccessFile randomAccessFile)
        throws IOException, GraphApiErrorException, InterruptedException {
        int exponentialBackoffMillis = EXPONENTIAL_BACKOFF_MILLIS_DEFAULT;
        int retryCount = 0;
        Range range = new Range(0, UPLOAD_CHUNK_SIZE);
        while (true) {
            byte[] bytesToUpload = getChunk(randomAccessFile, range);
            Request uploadRequest = new Request.Builder()
                .addHeader("Content-Range", String.format("bytes %d-%d/%d",
                    range.start, range.start + bytesToUpload.length - 1, randomAccessFile.length()))
                .url(uploadURL)
                .put(RequestBody.create(bytesToUpload, zipMediaType))
                .build();
            try (Response uploadResponse = DriveBackup.httpClient.newCall(uploadRequest).execute()) {
                //noinspection DataFlowIssue (response.body() is non-null after Call.execute())
                String uploadResponseBody = uploadResponse.body().string();
                if (uploadResponse.code() == 202) {
                    JSONObject responseObject = new JSONObject(uploadResponseBody);
                    JSONArray expectedRanges = responseObject.getJSONArray("nextExpectedRanges");
                    range = new Range(expectedRanges.getString(0), UPLOAD_CHUNK_SIZE);
                    exponentialBackoffMillis = EXPONENTIAL_BACKOFF_MILLIS_DEFAULT;
                    retryCount = 0;
                } else if (uploadResponse.code() == 201 || uploadResponse.code() == 200) {
                    break;
                } else {
                    if (retryCount > MAX_RETRY_ATTEMPTS || uploadResponse.code() == 409) {
                        Request cancelRequest = new Request.Builder().url(uploadURL).delete().build();
                        DriveBackup.httpClient.newCall(cancelRequest).execute().close();
                        throw new GraphApiErrorException(uploadResponse.code(), uploadResponseBody);
                    } else if (uploadResponse.code() == 404) {
                        throw new GraphApiErrorException(uploadResponse.code(), uploadResponseBody);
                    } else if (uploadResponse.code() == 416) {
                        Request statusRequest = new Request.Builder().url(uploadURL).build();
                        try (Response statusResponse = DriveBackup.httpClient.newCall(statusRequest).execute()) {
                            //noinspection DataFlowIssue (response.body() is non-null after Call.execute())
                            String statusResponseBody = statusResponse.body().string();
                            if (!statusResponse.isSuccessful()) {
                                throw new GraphApiErrorException(statusResponse.code(), statusResponseBody);
                            }
                            JSONObject responseObject = new JSONObject(statusResponseBody);
                            JSONArray expectedRanges = responseObject.getJSONArray("nextExpectedRanges");
                            range = new Range(expectedRanges.getString(0), UPLOAD_CHUNK_SIZE);
                        }
                    } else if (uploadResponse.code() >= 500 && uploadResponse.code() < 600) {
                        TimeUnit.MILLISECONDS.sleep(exponentialBackoffMillis);
                        exponentialBackoffMillis *= EXPONENTIAL_BACKOFF_FACTOR;
                    }
                    retryCount++;
                }
            }
        }
    }

    /**
     * Deletes the oldest files in the specified folder past the number to retain from the authenticated user's OneDrive.
     * <p>
     * The number of files to retain is specified by the user in the {@code config.yml}
     * @param parent the folder containing the files
     * @throws IOException on request execution failure
     * @throws GraphApiErrorException if the children could not be retrieved
     * @throws JSONException if the response does not contain the expected items
     */
    private void pruneBackups(@NotNull FQID parent) throws IOException, GraphApiErrorException {
        int fileLimit = ConfigParser.getConfig().backupStorage.keepCount;
        if (fileLimit == -1) {
            return;
        }
        List<JSONObject> childItems = getChildren(parent, "?$select=id,createdDateTime");
        if(fileLimit >= childItems.size()) {
            return;
        }
        logger.info(
            intl("backup-method-limit-reached"),
            "file-count", String.valueOf(childItems.size()),
            "upload-method", getName(),
            "file-limit", String.valueOf(fileLimit));
        childItems.sort(Comparator.comparing(item -> item.getString("createdDateTime")));
        int itemsToDelete = childItems.size() - fileLimit;
        for (int i = 0; i < itemsToDelete; i++) {
            String itemId = childItems.get(i).getString("id");
            recycleItem(parent.driveId, itemId);
        }
    }

    /**
     * A range of bytes
     */
    private static class Range {
        private final long start;
        private final int length;

        /**
         * Creates an instance of the {@link OneDriveUploader.Range Range} object
         * @param start the index of the first byte
         * @param length of the range
         */
        public Range(long start, int length) {
            this.start = start;
            this.length = length;
        }

        /**
         * Creates an instance of the {@link OneDriveUploader.Range Range} object
         * @param range in the format of {@code 000-000 or 000-}
         * @param maxLength to clamp the range to
         * @throws NumberFormatException if parseLong fails on range
         * @throws IndexOutOfBoundsException if range has no {@code -}
         */
        public Range(@NotNull String range, int maxLength) {
            int dash = range.indexOf('-');
            this.start = Long.parseLong(range.substring(0, dash));
            String rhs = range.substring(dash + 1);
            long end = Long.MAX_VALUE;
            if (!rhs.isEmpty()) {
                end = Long.parseLong(rhs);
            }
            this.length = (int)Math.min((end - start) + 1, maxLength);
        }
    }

    /**
     * gets an array of bytes to upload next from the file buffer
     * @param raf file to get chunk from
     * @param range in file to get chunk from
     * @return the array of bytes; may be smaller than range if {@code raf.length() - range.start < range.length}
     * @throws IOException on file read errors
     */
    private static byte @NotNull [] getChunk(RandomAccessFile raf, Range range) throws IOException {
        if (range.start >= raf.length()) {
            return new byte[0];
        }
        int chunkSize = (int)Math.min(range.length, raf.length() - range.start);
        byte[] bytes = new byte[chunkSize];
        raf.seek(range.start);
        raf.read(bytes);
        return bytes;
    }
}

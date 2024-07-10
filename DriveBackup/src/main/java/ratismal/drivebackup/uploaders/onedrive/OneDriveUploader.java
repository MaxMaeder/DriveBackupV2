package ratismal.drivebackup.uploaders.onedrive;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by Redemption on 2/24/2016.
 */
public final class OneDriveUploader extends Uploader {
    private static final int EXPONENTIAL_BACKOFF_MILLIS_DEFAULT = 1000;
    private static final int EXPONENTIAL_BACKOFF_FACTOR = 5;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String UPLOADER_NAME = "OneDrive";
    private static final String ID = "onedrive";
    /**
     * Size of the file chunks to upload to OneDrive
     */
    private static final int CHUNK_SIZE = 5 * 1024 * 1024;
    
    private long totalUploaded;
    private String accessToken = "";
    private String refreshToken;

    /**
     * File upload buffer
     */
    private RandomAccessFile raf;
    
    /**
     * Creates an instance of the {@code OneDriveUploader} object
     */
    public OneDriveUploader(DriveBackupInstance instance, UploadLogger logger) {
        super(instance, UPLOADER_NAME, ID, AuthenticationProvider.ONEDRIVE, logger);
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
        Response response = HttpClient.getHttpClient().newCall(request).execute();
        ResponseBody body = response.body();
        if (body == null) {
            throw new IOException("Response Body is null");
        }
        JSONObject parsedResponse = new JSONObject(body.string());
        response.close();
        if (!response.isSuccessful()) {
            return;
        }
        accessToken = parsedResponse.getString("access_token");
    }
    @Contract (pure = true)
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
            String destination = getRemoteSaveDirectory();
            Request request = new Request.Builder()
            String destination = normalizePath(ConfigParser.getConfig().backupStorage.remoteDirectory);
            FQID destinationId = createPath(destination);
            Request uploadRequest = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/drives/" + destinationId.driveId + "/items/" + destinationId.itemId
                        + ":/" + testFile.getName() + ":/content")
                .put(RequestBody.create(testFile, MediaType.parse("plain/txt")))
                .build();
            Response response = HttpClient.getHttpClient().newCall(request).execute();
            int statusCode = response.code();
            response.close();
            if (statusCode != 201) {
                setErrorOccurred(true);
            String testFileId;
            try (Response response = DriveBackup.httpClient.newCall(uploadRequest).execute()) {
                if (response.code() != 201) {
                    setErrorOccurred(true);
                }
                JSONObject parsedResponse = new JSONObject(response.body().string());
                testFileId = parsedResponse.getString("id");
            }
            TimeUnit.SECONDS.sleep(5L);
            request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/drives/" + destinationId.driveId + "/items/" + testFileId)
                .delete()
                .build();
            response = HttpClient.getHttpClient().newCall(request).execute();
            statusCode = response.code();
            response.close();
            if (statusCode != 204) {
                setErrorOccurred(true);
            }
        } catch (Exception exception) {
            NetUtil.catchException(exception, "graph.microsoft.com", logger);
            instance.getLoggingHandler().error("Error occurred while testing OneDrive", exception);
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
            String destination = getRemoteSaveDirectory();
            Collection<String> typeFolders = new ArrayList<>();
            Collections.addAll(typeFolders, destination.split("/"));
            Collections.addAll(typeFolders, type.split("[/\\\\]"));
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
            String destinationRoot = normalizePath(ConfigParser.getConfig().backupStorage.remoteDirectory);
            String destinationPath = concatPath(destinationRoot, location);
            FQID destinationId = createPath(destinationPath);
            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + folder.getPath() + "/" + file.getName() + ":/createUploadSession")
                .post(RequestBody.create("{}", UploaderUtils.getJsonMediaType()))
                .url("https://graph.microsoft.com/v1.0/drives/" + destinationId.driveId
                        + "/items/" + destinationId.itemId+ ":/" + file.getName() + ":/createUploadSession")
                .post(RequestBody.create("{}", jsonMediaType))
                .build();
            JSONObject parsedResponse;
            try (Response response = HttpClient.getHttpClient().newCall(request).execute()) {
                ResponseBody body = response.body();
                if (body == null) {
                    throw new IOException("Response Body is null");
                }
                parsedResponse = new JSONObject(body.string());
            }
            String uploadURL = parsedResponse.getString("uploadUrl");
            raf = new RandomAccessFile(file, "r");
            int exponentialBackoffMillis = EXPONENTIAL_BACKOFF_MILLIS_DEFAULT;
            int retryCount = 0;
            while (true) {
                byte[] bytesToUpload = getChunk();
                request = new Request.Builder()
                    .addHeader("Content-Range", String.format("bytes %d-%d/%d", getTotalUploaded(), getTotalUploaded() + bytesToUpload.length - 1L, file.length()))
                    .url(uploadURL)
                    .put(RequestBody.create(bytesToUpload, UploaderUtils.getZipMediaType()))
                    .build();
                try (Response uploadResponse = HttpClient.getHttpClient().newCall(request).execute()) {
                    if (uploadResponse.code() == 202) {
                        ResponseBody responseBody = uploadResponse.body();
                        if (responseBody == null) {
                            throw new IOException("Response Body is null");
                        }
                        parsedResponse = new JSONObject(responseBody.string());
                        List<Object> nextExpectedRanges = parsedResponse.getJSONArray("nextExpectedRanges").toList();
                        setRanges(nextExpectedRanges.toArray(new String[0]));
                        exponentialBackoffMillis = EXPONENTIAL_BACKOFF_MILLIS_DEFAULT;
                        retryCount = 0;
                    } else if (uploadResponse.code() == 201 || uploadResponse.code() == 200) {
                        break;
                    } else {
                        // conflict after successful upload not handled
                    } else { // TODO conflict after successful upload not handled
                        if (retryCount > MAX_RETRY_ATTEMPTS) {
                            request = new Request.Builder().url(uploadURL).delete().build();
                            HttpClient.getHttpClient().newCall(request).execute().close();
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
                pruneBackups(destinationId);
            } catch (Exception e) {
                logger.log("backup-method-prune-failed");
                throw e;
            }
        } catch (Exception exception) {
            NetUtil.catchException(exception, "graph.microsoft.com", logger);
            instance.getLoggingHandler().error("Error occurred while uploading to OneDrive", exception);
            setErrorOccurred(true);
        }
        if (raf != null) {
            raf.close();
        }
    }

    /**
    * Closes any remaining connections retrieveNewAccessToken
    */
    @Contract (pure = true)
    public void close() {
        // nothing needs to be done
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
     * removes "." and ".." segments from the path
     * @param path to normalize
     * @return the normalized path
     */
    @NotNull
    private String normalizePath(@NotNull String path) {
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
     * joins the two paths with '/' while handling emptiness of either side
     * @param lhs left hand side of to be joined path
     * @param rhs right hand side of to be joined path
     * @return joined path
     */
    @NotNull
    private String concatPath(@NotNull String lhs, @NotNull String rhs) {
        if (rhs.isEmpty()) {
            return lhs;
        }
        if (lhs.isEmpty()) {
            return rhs;
        }
        return lhs + '/' + rhs;
    }

    /**
     * creates all folders in the path if they don't already exist
     * @param path to create the folders for
     * @return FQID of the last folder in the path
     * @throws IOException if the folder could not be created;
     * or if the api request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    @NotNull
    private FQID createPath(@NotNull String path) throws IOException {
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
     * @return FQID of the folder
     * @throws IOException if the folder could not be created;
     * or if the api request could not be executed due to cancellation, a connectivity problem or timeout.
     */
    @NotNull
    private FQID createFolder(@NotNull FQID root,@NotNull String folder) throws IOException {
        FQID item = getFolder(root, folder);
        if (item != null) {
            return item;
        }
        RequestBody requestBody = RequestBody.create("{ \"name\": \"" + folder
                + "\", \"folder\": {}, \"@microsoft.graph.conflictBehavior\": \"fail\" }", jsonMediaType);
        Request request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + parent.getPath())
            .build();
        Response response = HttpClient.getHttpClient().newCall(request).execute();
        ResponseBody body = response.body();
        if (body == null) {
            throw new IOException("Response Body is null");
        }
        JSONObject parsedResponse = new JSONObject(body.string());
        response.close();
        String parentId = parsedResponse.getString("id");
        RequestBody requestBody = RequestBody.create(
            "{" +
            " \"name\": \"" + name + "\"," +
            " \"folder\": {}," +
            " \"@name.conflictBehavior\": \"fail\"" +
            "}", UploaderUtils.getJsonMediaType());
        request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://graph.microsoft.com/v1.0/me/drive/items/" + parentId + "/children")
            .post(requestBody)
            .build();
        response = HttpClient.getHttpClient().newCall(request).execute();
        boolean folderCreated = response.isSuccessful();
        response.close();
        if (!folderCreated) {
            throw new IOException("Couldn't create folder " + name);
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/drives/" + root.driveId + "/items/" + root.itemId + "/children")
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
    private FQID createRootFolder(@NotNull String folder) throws IOException {
        FQID item = getRootFolder(folder);
        if (item != null) {
            return item;
        }
        RequestBody requestBody = RequestBody.create("{ \"name\": \""
                + folder + "\", \"folder\": {}, \"@name.conflictBehavior\": \"fail\" }", jsonMediaType);
        RequestBody requestBody = RequestBody.create(
            "{" +
            " \"name\": \"" + name + "\"," +
            " \"folder\": {}," +
            " \"@name.conflictBehavior\": \"fail\"" +
            "}", UploaderUtils.getJsonMediaType());
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
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://graph.microsoft.com/v1.0/me/drive/root/children")
            .post(requestBody)
            .build();
        Response response = HttpClient.getHttpClient().newCall(request).execute();
        boolean folderCreated = response.isSuccessful();
        response.close();
        if (!folderCreated) {
            throw new IOException("Couldn't create folder " + name);
        }
    }

    /**
     * tries to find folder in the drive root
     * @param folder to search
     * @return FQID or null if not found
     */
    @Nullable
    private FQID getRootFolder(@NotNull String folder) {
        try {
            String folderUrl = folder.isEmpty() ? folder : ":/" + folder;
            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/me/drive/root" + folderUrl + "?$select=id,parentReference,remoteItem")
                .build();
            JSONObject parsedResponse;
            try (Response response = DriveBackup.httpClient.newCall(request).execute()) {
                parsedResponse = new JSONObject(response.body().string());
            }
            if (parsedResponse.has("remoteItem")) {
               parsedResponse = parsedResponse.optJSONObject("remoteItem");
            Response response = HttpClient.getHttpClient().newCall(request).execute();
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response Body is null");
            }
            JSONObject parsedResponse = new JSONObject(body.string());
            response.close();
            JSONArray jsonArray = parsedResponse.getJSONArray("value");
            for (int i = 0; i < jsonArray.length(); i++) {
                String folderName = jsonArray.getJSONObject(i).getString("name");
                if (name.equals(folderName)) {
                    return parent.add(name);
                }
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
    private FQID getFolder(@NotNull FQID root, @NotNull String folder) {
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
                String folderName = childItem.getString("name"); // TODO filter non folders
                if (folder.equals(folderName)) {
                    if (childItem.has("remoteItem")) {
                        childItem = childItem.optJSONObject("remoteItem");
                    }
                    String driveId = childItem.getJSONObject("parentReference").getString("driveId");
                    String itemId = childItem.getString("id");
                    return new FQID(driveId, itemId);
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/me/drive/root/children")
                .build();
            Response response = HttpClient.getHttpClient().newCall(request).execute();
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response Body is null");
            }
            JSONObject parsedResponse = new JSONObject(body.string());
            response.close();
            JSONArray jsonArray = parsedResponse.getJSONArray("value");
            for (int i = 0; i < jsonArray.length(); i++) {
                String folderName = jsonArray.getJSONObject(i).getString("name");
                if (name.equals(folderName)) {
                    return new File().add(name);
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
     * @throws IOException on request execution failure
     * @throws JSONException if the response does not contain the expected items
     */
    private void pruneBackups(@NotNull FQID parent) throws Exception, JSONException {
        int fileLimit = ConfigParser.getConfig().backupStorage.keepCount;
    private void pruneBackups(File parent) throws Exception {
        int fileLimit = getKeepCount();
        if (fileLimit == -1) {
            return;
        }
        Request childItemRequest = new Request.Builder()
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://graph.microsoft.com/v1.0/drives/" + parent.driveId + "/items/" + parent.itemId + "/children?sort_by=createdDateTime")
            .build();
        Response response = HttpClient.getHttpClient().newCall(request).execute();
        ResponseBody body = response.body();
        if (body == null) {
            throw new IOException("Response Body is null");
        }
        JSONObject parsedResponse = new JSONObject(body.string());
        response.close();
        List<String> fileIDs = new ArrayList<>();
        JSONArray jsonArray = parsedResponse.getJSONArray("value");
        for (int i = 0; i < jsonArray.length(); i++) {
            fileIDs.add(jsonArray.getJSONObject(i).getString("id"));
        JSONArray items;
        try (Response childItemResponse = DriveBackup.httpClient.newCall(childItemRequest).execute()) {
            JSONObject parsedResponse = new JSONObject(childItemResponse.body().string());
            items = parsedResponse.getJSONArray("value");
        }
        if(fileLimit >= items.length()) { // TODO filter non backup files (folders & files not created by plugin)
            return;
        }
        logger.info(
            intl("backup-method-limit-reached"),
            "file-count", String.valueOf(items.length()),
            "upload-method", getName(),
            "file-limit", String.valueOf(fileLimit));
        int itemsToDelete = items.length() - fileLimit;
        for (int i = 0; i < itemsToDelete; i++) {
            String fileIDValue = items.getJSONObject(i).getString("id");
            Request deleteRequest = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/drives/" + parent.driveId + "/items/" + fileIDValue)
                .delete()
                .build();
            DriveBackup.httpClient.newCall(deleteRequest).execute().close(); // TODO handle deletion failure
        if(fileLimit < fileIDs.size()){
            Map<String, String> placeholders = new HashMap<>(3);
            placeholders.put("file-count", String.valueOf(fileIDs.size()));
            placeholders.put("upload-method", getName());
            placeholders.put("file-limit", String.valueOf(fileLimit));
            logger.info("backup-method-limit-reached", placeholders);
        }
        for (Iterator<String> iterator = fileIDs.listIterator(); iterator.hasNext(); ) {
            String fileIDValue = iterator.next();
            if (fileLimit < fileIDs.size()) {
                request = new Request.Builder()
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .url("https://graph.microsoft.com/v1.0/me/drive/items/" + fileIDValue)
                    .delete()
                    .build();
                HttpClient.getHttpClient().newCall(request).execute().close();
                iterator.remove();
            }
            if (fileIDs.size() <= fileLimit){
                break;
            }
        }
    }

    /**
     * A file/folder in the authenticated user's OneDrive
     */
    private static final class File {
        private final ArrayList<String> filePath = new ArrayList<>();

        /**
         * Creates a reference of the {@code File} object
         */
        File() {
        }

        /**
         * Returns a {@code File} with the specified folder added to the file path.
         * @param folder the {@code File}
         */
        @NotNull
        private File add(String folder) {
            File childFile = new File();
            if (getPath().isEmpty()) {
                childFile.setPath(folder);
            } else {
                childFile.setPath(getPath() + "/" + folder);
            }
            return childFile;
        }
        // TODO handle @odata.nextLink

        /**
         * Sets the path of the file/folder
         * @param path the path, as an {@code String}
         */
        private void setPath(@NotNull String path) {
            filePath.clear();
            Collections.addAll(filePath, path.split("/"));
        }

        /**
         * Gets the path of the file/folder
         * @return the path, as a {@code String}
         */
        @NotNull
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
         * Gets the path of the parent folder of the file/folder.
         * @return the path, as a String
         */
        @NotNull
        private String getParent() {
            List<String> parentPath = new ArrayList<>(filePath);
            parentPath.remove(parentPath.size() - 1);
            return String.join("/", parentPath);
        }
    }

    /**
     * A range of bytes
     */
    private static final class Range {
        private final long start;
        private final long end;

        /**
         * Creates an instance of the {@code Range} object
         * @param start the index of the first byte
         * @param end the index of the last byte
         */
        @Contract (pure = true)
        private Range(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Resets the number of bytes uploaded in the last chunk, and the number of bytes uploaded in total.
     */
    @Contract (mutates = "this")
    private void resetRanges() {
        lastUploaded = 0L;
        totalUploaded = 0L;
        totalUploaded = 0;
    }
    
    /**
     * Sets the number of bytes uploaded in the last chunk,
     * and the number of bytes uploaded in total from the ranges of bytes the OneDrive API requested to be uploaded last.
     * @param stringRanges the ranges of bytes requested
     */
    private void setRanges(@NotNull String @NotNull [] stringRanges) {
        Range[] ranges = new Range[stringRanges.length];
        for (int i = 0; i < stringRanges.length; i++) {
            long start = Long.parseLong(stringRanges[i].substring(0, stringRanges[i].indexOf('-')));
            String s = stringRanges[i].substring(stringRanges[i].indexOf('-') + 1);
            long end = 0L;
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
     * @throws IOException on file read errors
     */
    private byte @NotNull [] getChunk() throws IOException {
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
    @Contract (pure = true)
    private long getTotalUploaded() {
        return totalUploaded;
    }
}

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
    public void test(java.io.File testFile) {
        try {
            String destination = ConfigParser.getConfig().backupStorage.remoteDirectory;
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
                .delete()
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
     * Uploads the specified file to the authenticated user's OneDrive inside a folder for the specified file type.
     * @param file the file
     * @param type the type of file (ex. plugins, world)
     */
    public void uploadFile(java.io.File file, String type) throws IOException {
        try {
            resetRanges();
            String destination = ConfigParser.getConfig().backupStorage.remoteDirectory;
            ArrayList<String> typeFolders = new ArrayList<>();
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
            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + folder.getPath() + "/" + file.getName() + ":/createUploadSession")
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
                pruneBackups(folder);
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
     * Creates a folder with the specified name in the specified parent folder in the authenticated user's OneDrive.
     * @param name the name of the folder
     * @param parent the parent folder
     * @return the created folder
     * @throws IOException
     */
    @NotNull
    private File createFolder(String name, File parent) throws IOException {
        File file = getFolder(name, parent);
        if (file != null) {
            return file;
        }
        Request request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + parent.getPath())
            .build();
        Response response = DriveBackup.httpClient.newCall(request).execute();
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
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://graph.microsoft.com/v1.0/me/drive/items/" + parentId + "/children")
            .post(requestBody)
            .build();
        response = DriveBackup.httpClient.newCall(request).execute();
        boolean folderCreated = response.isSuccessful();
        response.close();
        if (!folderCreated) {
            throw new IOException("Couldn't create folder " + name);
        }
        return parent.add(name);
    }

    /**
     * Creates a folder with the specified name in the root of the authenticated user's OneDrive.
     * @param name the name of the folder
     * @return the created folder
     * @throws IOException
     */
    @NotNull
    private File createFolder(String name) throws IOException {
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
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://graph.microsoft.com/v1.0/me/drive/root/children")
            .post(requestBody)
            .build();
        Response response = DriveBackup.httpClient.newCall(request).execute();
        boolean folderCreated = response.isSuccessful();
        response.close();
        if (!folderCreated) {
            throw new IOException("Couldn't create folder " + name);
        }
        return new File().add(name);
    }

    /**
     * Returns the folder in the specified parent folder of the authenticated user's OneDrive with the specified name.
     * @param name the name of the folder
     * @param parent the parent folder
     * @return the folder or {@code null}
     */
    @Nullable
    private File getFolder(String name, File parent) {
        try {
            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + parent.getPath() + ":/children")
                .build();
            Response response = DriveBackup.httpClient.newCall(request).execute();
            JSONObject parsedResponse = new JSONObject(response.body().string());
            response.close();
            JSONArray jsonArray = parsedResponse.getJSONArray("value");
            for (int i = 0; i < jsonArray.length(); i++) {
                String folderName = jsonArray.getJSONObject(i).getString("name");
                if (name.equals(folderName)) {
                    return parent.add(name);
                }
            }
        } catch (Exception exception) {
            return null;
        }
        return null;
    }

    /**
     * Returns the folder in the root of the authenticated user's OneDrive with the specified name.
     * @param name the name of the folder
     * @return the folder or {@code null}
     */
    @Nullable
    private File getFolder(String name) {
        try {
            Request request = new Request.Builder()
                .addHeader("Authorization", "Bearer " + accessToken)
                .url("https://graph.microsoft.com/v1.0/me/drive/root/children")
                .build();
            Response response = DriveBackup.httpClient.newCall(request).execute();
            JSONObject parsedResponse = new JSONObject(response.body().string());
            response.close();
            JSONArray jsonArray = parsedResponse.getJSONArray("value");
            for (int i = 0; i < jsonArray.length(); i++) {
                String folderName = jsonArray.getJSONObject(i).getString("name");
                if (name.equals(folderName)) {
                    return new File().add(name);
                }
            }
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
    private void pruneBackups(File parent) throws Exception {
        int fileLimit = ConfigParser.getConfig().backupStorage.keepCount;
        if (fileLimit == -1) {
            return;
        }
        Request request = new Request.Builder()
            .addHeader("Authorization", "Bearer " + accessToken)
            .url("https://graph.microsoft.com/v1.0/me/drive/root:/" + parent.getPath() + ":/children?sort_by=createdDateTime")
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

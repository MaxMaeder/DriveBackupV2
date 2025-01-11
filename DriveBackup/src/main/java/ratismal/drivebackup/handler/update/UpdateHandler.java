package ratismal.drivebackup.handler.update;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ratismal.drivebackup.handler.logging.PrefixedLogger;
import ratismal.drivebackup.http.HttpClient;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.util.Version;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

public final class UpdateHandler {
    
    private static final String LATEST_URL = "https://api.github.com/repos/MaxMaeder/DriveBackupV2/releases/latest";
    private static final String TEMP_FILE_NAME = "DriveBackupV2.jar.temp";
    
    private final DriveBackupInstance instance;
    private UpdateTask updateTask;
    private final PrefixedLogger logger;
    private Version current;
    private Version latest;
    private String latestDownloadUrl;
    
    @Contract (pure = true)
    public UpdateHandler(@NotNull DriveBackupInstance instance) {
        this.instance = instance;
        logger = instance.getLoggingHandler().getPrefixedLogger("UpdateHandler");
    }
    
    public void setup() {
        boolean updateCheckEnabled = instance.getConfigHandler().getConfig().getValue("advanced", "updateCheckEnabled").getBoolean();
        if (updateCheckEnabled) {
            logger.info("Starting update check thread...");
            updateTask = new UpdateTask(instance, this);
            current = instance.getCurrentVersion();
            instance.getTaskHandler().scheduleRepeatingTask(0L, 6L, TimeUnit.HOURS, updateTask);
        } else {
            logger.info("Update check is disabled");
        }
    }
    
    public void getLatest() {
        try {
            Request request = new Request.Builder().url(LATEST_URL).build();
            Response response = HttpClient.getHttpClient().newCall(request).execute();
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response body is null");
            }
            JSONObject pluginVersions = new JSONObject(body.string());
            response.close();
            if (pluginVersions.isEmpty()) {
                throw new IOException("No versions found");
            }
            int index = pluginVersions.length() - 1;
            String htmlUrl = pluginVersions.getString("html_url");
            String assetsUrl = pluginVersions.getString("assets_url");
            Request request2 = new Request.Builder().url(assetsUrl).build();
            JSONArray assets;
            try (Response response2 = HttpClient.getHttpClient().newCall(request2).execute()) {
                if (response2.code() != 200) {
                    throw new IOException("Unexpected response: " + response2.code() + " : " + response2.message());
                }
                ResponseBody body2 = response2.body();
                if (body2 == null) {
                    throw new IOException("Response body is null");
                }
                assets = new JSONArray(body2.string());
            }
            JSONObject jar = assets.getJSONObject(0);
            String versionTitle = htmlUrl.substring(htmlUrl.lastIndexOf('/') + 2).trim();
            latest = Version.parse(versionTitle);
            latestDownloadUrl = jar.getString("url");
        } catch (IOException | JSONException e) {
            logger.error("Failed to get latest version: " + e.getMessage());
        }
    }
    
    @Contract (pure = true)
    public boolean hasUpdate() {
        if (latest == null || latestDownloadUrl == null || current == null) {
            return false;
        }
        return latest.isNewerThan(current);
    }
    
    public void downloadUpdate() {
        try {
            File tempFile = new File(instance.getJarFile().getParentFile(), TEMP_FILE_NAME);
            Request request = new Request.Builder().url(latestDownloadUrl).addHeader("Accept", "application/octet-stream").build();
            try (Response response = HttpClient.getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to download file: " + response);
                }
                try (ResponseBody body = response.body()) {
                    if (body == null) {
                        throw new IOException("Response body is null");
                    }
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        fos.write(body.bytes());
                        logger.info("Update downloaded successfully");
                    }
                }
            }
            Files.move(tempFile.toPath(), instance.getJarFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Update installed successfully, please restart the server to apply changes");
        } catch (IOException e) {
            logger.error("Failed to download update: " + e.getMessage());
        }
    }
    
    public Version getCurrentVersion() {
        return current;
    }
    
    public Version getLatestVersion() {
        return latest;
    }
    
}

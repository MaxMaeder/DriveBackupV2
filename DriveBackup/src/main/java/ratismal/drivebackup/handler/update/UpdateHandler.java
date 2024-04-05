package ratismal.drivebackup.handler.update;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.Contract;
import org.json.JSONArray;
import org.json.JSONException;
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
import java.util.regex.Pattern;

public class UpdateHandler {
    
    private static final int CURSE_PROJECT_ID = 383461;
    private static final String CURSE_REQUEST_URL = "https://api.curseforge.com/servermods/files?projectids=" + CURSE_PROJECT_ID;
    private static final Pattern NAME_DASH = Pattern.compile("DriveBackupV2-", Pattern.LITERAL);
    private static final String tempFileName = "DriveBackupV2.jar.temp";
    
    private final DriveBackupInstance instance;
    private UpdateTask updateTask;
    private final PrefixedLogger logger;
    private Version current;
    private Version latest;
    private String latestDownloadUrl;
    
    @Contract (pure = true)
    public UpdateHandler(DriveBackupInstance instance) {
        this.instance = instance;
        logger = instance.getLoggingHandler().getPrefixedLogger("UpdateHandler");
    }
    
    public void setup() {
        boolean updateCheckEnabled = instance.getConfigHandler().getConfig().getValue("advanced", "updateCheckEnabled").getBoolean();
        if (updateCheckEnabled) {
            logger.info("Starting update check thread...");
            updateTask = new UpdateTask(instance, this);
            current = instance.getCurrentVersion();
            instance.getTaskHandler().scheduleAsyncRepeatingTask(updateTask, 0L, 6L, TimeUnit.HOURS);
        } else {
            logger.info("Update check is disabled");
        }
    }
    
    public void getLatest() {
        try {
            Request request = new Request.Builder().url(CURSE_REQUEST_URL).build();
            Response response = HttpClient.getHttpClient().newCall(request).execute();
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response body is null");
            }
            JSONArray pluginVersions = new JSONArray(body.string());
            response.close();
            if (pluginVersions.isEmpty()) {
                throw new IOException("No versions found");
            }
            int index = pluginVersions.length() - 1;
            String versionTitle = pluginVersions.getJSONObject(index).getString("name");
            String downloadURL = pluginVersions.getJSONObject(index).getString("downloadUrl");
            latest = Version.parse(versionTitle);
            latestDownloadUrl = downloadURL;
        } catch (IOException | JSONException e) {
            logger.error("Failed to get latest version: " + e.getMessage());
        }
    }
    
    public boolean hasUpdate() {
        if (latest == null || latestDownloadUrl == null || current == null) {
            return false;
        }
        return latest.isNewerThan(current);
    }
    
    public void downloadUpdate() {
        try {
            File tempFile = new File(instance.getJarFile().getParentFile(), "DriveBackupV2.jar.temp");
            Request request = new Request.Builder().url(latestDownloadUrl).build();
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

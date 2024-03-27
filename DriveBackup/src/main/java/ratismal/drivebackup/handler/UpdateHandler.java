package ratismal.drivebackup.handler;

import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ratismal.drivebackup.handler.logging.PrefixedLogger;
import ratismal.drivebackup.http.HttpClient;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.plugin.updater.UpdateChecker;
import ratismal.drivebackup.util.Version;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class UpdateHandler {
    
    private static final int CURSE_PROJECT_ID = 383461;
    // 6 hours` in seconds
    private static final long UPDATE_CHECK_INTERVAL = 60 * 60 * 6;
    private static final Pattern NAME_DASH = Pattern.compile("DriveBackupV2-", Pattern.LITERAL);
    private static final String tempFileName = "DriveBackupV2.jar.temp";
    private final DriveBackupInstance instance;
    private Version currentVersion;
    private Version latestVersion;
    private String latestDownloadURL;
    private final File jarFile;
    private final File tempFile;
    private final PrefixedLogger logger;
    
    @Contract (pure = true)
    public UpdateHandler(@NotNull DriveBackupInstance instance) {
        this.instance = instance;
        jarFile = instance.getJarFile();
        tempFile = new File(jarFile.getParentFile(), tempFileName);
        logger = instance.getLoggingHandler().getPrefixedLogger("UpdateHandler");
    }
    
    public void checkForUpdate() {
        logger.info("Running automatic update check");
        try {
            getLatestVersion();
        } catch (IOException | JSONException | NumberFormatException e) {
            logger.error("Failed to check for updates", e);
            return;
        }
        getCurrentVersion();
        if (latestVersion.isNewerThan(currentVersion)) {
            logger.info("New version available: " + latestVersion);
            logger.info("Use /drivebackup update to update to the latest version");
        } else {
            logger.info("No new version available");
        }
    
    }
    
    public void startUpdateThread() {
        if (instance.getConfigHandler().getConfig().node("advanced", "updateCheckEnabled").getBoolean(true)) {
            logger.info("Starting automatic update checker thread");
            instance.getTaskHandler().scheduleAsyncRepeatingTask(this::checkForUpdate, 0L, UPDATE_CHECK_INTERVAL, TimeUnit.SECONDS);
            logger.info("Automatic update checker thread started");
        } else {
            logger.info("Automatic update checker is disabled");
        }
    }
    
    public boolean hasUpdate() {
        if (latestVersion == null) {
            return false;
        }
        return latestVersion.isNewerThan(currentVersion);
    }
    
    public void preformUpdate() {
        if (!hasUpdate()) {
            logger.info("No update available");
            return;
        }
        if (latestDownloadURL == null) {
            logger.error("Failed to get download URL");
            return;
        }
        logger.info("Starting update");
        try {
            downloadUpdate();
            Files.move(tempFile.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Update successful");
        } catch (IOException e) {
            logger.error("Failed to update", e);
        }
    }
    
    private void downloadUpdate() throws IOException {
        Request request = new Request.Builder().url(UpdateChecker.getLatestDownloadUrl()).build();
        try (Response response = HttpClient.getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download file: " + response);
            }
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(response.body().bytes());
            }
        }
    }
    
    private void getLatestVersion() throws IOException, JSONException, NumberFormatException {
        Request request = new Request.Builder()
                .url("https://api.curseforge.com/servermods/files?projectids=" + CURSE_PROJECT_ID)
                .build();
        Response response = HttpClient.getHttpClient().newCall(request).execute();
        JSONArray pluginVersions = new JSONArray(response.body().string());
        response.close();
        if (pluginVersions.isEmpty()) {
            throw new NumberFormatException();
        }
        JSONObject jsonObject = pluginVersions.getJSONObject(pluginVersions.length() - 1);
        String name = jsonObject.getString("name");
        String versionTitle = NAME_DASH.matcher(name).replaceAll("").trim();
        latestDownloadURL = jsonObject.getString("downloadUrl");
        latestVersion = Version.parse(versionTitle);
    }
    
    private void getCurrentVersion() {
        currentVersion = instance.getCurrentVersion();
    }
    
}

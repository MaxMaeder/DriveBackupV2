package ratismal.drivebackup.plugin.updater;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.Contract;
import org.json.JSONArray;
import org.json.JSONObject;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.http.HttpClient;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.util.Logger;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.NetUtil;
import ratismal.drivebackup.util.SchedulerUtil;
import ratismal.drivebackup.util.Version;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import static ratismal.drivebackup.config.Localization.intl;

@Deprecated
public class UpdateChecker {

    /**
     * How often to check for updates, in seconds
     */
    private static final long UPDATE_CHECK_INTERVAL = TimeUnit.HOURS.toSeconds(4L);
    
    private static Version currentVersion;
    private static Version latestVersion;
    private static String latestDownloadUrl;

    private static boolean hasSentStartMessage;

    public static void updateCheck() {
        DriveBackup plugin = DriveBackup.getInstance();
        UpdateChecker checker = new UpdateChecker();
        if (ConfigParser.getConfig().advanced.updateCheckEnabled) {
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                Logger logger = (input, placeholders) -> MessageUtil.Builder().mmText(input, placeholders).send();
                try {
                    if (!hasSentStartMessage) {
                        logger.log(intl("update-checker-started"));
                        hasSentStartMessage = true;
                    }
                    //get versions
                    currentVersion = UpdateChecker.getCurrent();
                    latestVersion = UpdateChecker.getLatest();
                    //check if the current version is outdated
                    if (latestVersion.isNewerThan(currentVersion)) {
                        logger.log(
                            intl("update-checker-new-release"),
                            "latest-version", latestVersion.toString(),
                            "current-version", currentVersion.toString());
                    } else if (currentVersion.isNewerThan(latestVersion)) {
                        logger.log(
                            intl("update-checker-unsupported-release"),
                            "latest-version", latestVersion.toString(),
                            "current-version", currentVersion.toString());
                    }
                } catch (Exception e) {
                    NetUtil.catchException(e, "api.github.com", logger);
                    logger.log(intl("update-checker-failed"));
                    MessageUtil.sendConsoleException(e);
                }
            }, 0L, SchedulerUtil.sToTicks(UPDATE_CHECK_INTERVAL));
        }
    }

    /**
     * Gets whether an update is available for the plugin
     * @return whether an update is available
     */
    @Contract (pure = true)
    public static boolean isUpdateAvailable() {
        if (latestVersion != null) {
            return latestVersion.isNewerThan(currentVersion);
        }
        return false;
    }

    @Contract (pure = true)
    public static String getLatestDownloadUrl() {
        return latestDownloadUrl;
    }

    public static Version getCurrent() {
        String versionTitle = DriveBackup.getInstance().getDescription().getVersion().split("-")[0];
        return Version.parse(versionTitle);
    }

    public static Version getLatest() throws Exception {
        final String LATEST_URL = "https://api.github.com/repos/MaxMaeder/DriveBackupV2/releases/latest";
        Request request = new Request.Builder().url(LATEST_URL).build();
        JSONObject pluginVersions;
        try (Response response = HttpClient.getHttpClient().newCall(request).execute()) {
            if (response.code() != 200) {
                throw new IOException("Unexpected response: " + response.code() + " : " + response.message());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response body is null");
            }
            pluginVersions = new JSONObject(body.string());
        }
        if (pluginVersions.isEmpty()) {
            throw new NoSuchElementException("No plugin versions received");
        }
        String htmlUrl = pluginVersions.getString("html_url");
        String assetsUrl = pluginVersions.getString("assets_url");
        Request request2 = new Request.Builder().url(assetsUrl).build();
        JSONArray assets;
        try (Response response = HttpClient.getHttpClient().newCall(request2).execute()) {
            if (response.code() != 200) {
                throw new IOException("Unexpected response: " + response.code() + " : " + response.message());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response body is null");
            }
            assets = new JSONArray(body.string());
        }
        JSONObject jar = assets.getJSONObject(0);
        String versionTitle = htmlUrl.substring(htmlUrl.lastIndexOf('/') + 2).trim();
        latestDownloadUrl = jar.getString("url");
        return Version.parse(versionTitle);
    }
}

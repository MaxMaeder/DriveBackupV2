package ratismal.drivebackup.plugin.updater;

import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.Contract;
import org.json.JSONArray;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.http.HttpClient;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.util.Logger;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.NetUtil;
import ratismal.drivebackup.util.SchedulerUtil;
import ratismal.drivebackup.util.Version;

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import static ratismal.drivebackup.config.Localization.intl;

@Deprecated
public class UpdateChecker {
    private static final int CURSE_PROJECT_ID = 383461;

    /**
     * How often to check for updates, in seconds
     */
    private static final long UPDATE_CHECK_INTERVAL = TimeUnit.HOURS.toSeconds(4);

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
                    currentVersion = checker.getCurrent();
                    latestVersion = checker.getLatest();
                    //check if the current version is outdated
                    if (latestVersion.isAfter(currentVersion)) {
                        logger.log(
                            intl("update-checker-new-release"),
                            "latest-version", latestVersion.toString(),
                            "current-version", currentVersion.toString());
                    } else if (currentVersion.isAfter(latestVersion)) {
                        logger.log(
                            intl("update-checker-unsupported-release"),
                            "latest-version", latestVersion.toString(),
                            "current-version", currentVersion.toString());
                    }
                } catch (Exception e) {
                    NetUtil.catchException(e, "dev.bukkit.org", logger);
                    logger.log(intl("update-checker-failed"));
                    MessageUtil.sendConsoleException(e);
                }
            }, 0, SchedulerUtil.sToTicks(UPDATE_CHECK_INTERVAL));
        }
    }

    /**
     * Gets whether an update is available for the plugin
     * @return whether an update is available
     */
    @Contract (pure = true)
    public static boolean isUpdateAvailable() {
        if (latestVersion != null) {
            return latestVersion.isAfter(currentVersion);
        }
        return false;
    }

    @Contract (pure = true)
    public static String getLatestDownloadUrl() {
        return latestDownloadUrl;
    }

    public Version getCurrent() throws Exception {
        String versionTitle = DriveBackup.getInstance().getDescription().getVersion().split("-")[0];
        return Version.parse(versionTitle);
    }

    public Version getLatest() throws Exception {
        Request request = new Request.Builder()
            .url("https://api.curseforge.com/servermods/files?projectids=" + CURSE_PROJECT_ID)
            .build();
        JSONArray pluginVersions;
        try (Response response = HttpClient.getHttpClient().newCall(request).execute()) {
            if (response.code() != 200) {
                throw new Exception("Unexpected response: " + response.code() + " : " + response.message());
            }
            pluginVersions = new JSONArray(response.body().string());
        }
        if (pluginVersions.isEmpty()) {
            throw new NoSuchElementException("No plugin versions received");
        }
        String versionTitle = pluginVersions.getJSONObject(pluginVersions.length() - 1).getString("name").replace("DriveBackupV2-", "").trim();
        latestDownloadUrl = pluginVersions.getJSONObject(pluginVersions.length() - 1).getString("downloadUrl");
        return Version.parse(versionTitle);
    }
}

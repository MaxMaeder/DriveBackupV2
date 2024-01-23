package ratismal.drivebackup.plugin.updater;

import org.json.JSONArray;

import okhttp3.Request;
import okhttp3.Response;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.util.Logger;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.NetUtil;
import ratismal.drivebackup.util.SchedulerUtil;
import ratismal.drivebackup.util.Version;

import static ratismal.drivebackup.config.Localization.intl;

public class UpdateChecker {
    private static final int CURSE_PROJECT_ID = 383461;

    /**
     * How often to check for updates, in seconds
     */
    private static final long UPDATE_CHECK_INTERVAL = 60 * 60 * 4;

    private static Version currentVersion;
    private static Version latestVersion;
    private static String latestDownloadUrl;

    private static boolean hasSentStartMessage = false;

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

                    //check if current version is outdated
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
    public static boolean isUpdateAvailable() {
        if (latestVersion != null)
            return latestVersion.isAfter(currentVersion);
        return false;
    }

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

        Response response = DriveBackup.httpClient.newCall(request).execute();
        JSONArray pluginVersions = new JSONArray(response.body().string());
        response.close();

        if (pluginVersions.length() == 0) {
            throw new NumberFormatException();
        }

        String versionTitle = pluginVersions.getJSONObject(pluginVersions.length() - 1).getString("name").replace("DriveBackupV2-", "").trim();
        UpdateChecker.latestDownloadUrl = pluginVersions.getJSONObject(pluginVersions.length() - 1).getString("downloadUrl");
        return Version.parse(versionTitle);
    }
}

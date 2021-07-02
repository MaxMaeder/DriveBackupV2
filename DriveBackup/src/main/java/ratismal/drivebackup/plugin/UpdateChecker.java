package ratismal.drivebackup.plugin;

import java.io.IOException;
import java.net.UnknownHostException;

import org.json.JSONArray;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.SchedulerUtil;

public class UpdateChecker {
    private static final int BUKKIT_PROJECT_ID = 383461;

    /**
     * How often to check for updates, in seconds
     */
    private static final long UPDATE_CHECK_INTERVAL = 60 * 60 * 4;

    /**
     * Global instance of the HTTP client
     */
    private static final OkHttpClient httpClient = new OkHttpClient();

    private static double currentVersion;
    private static double latestVersion;

    public static void updateCheck() {
        DriveBackup plugin = DriveBackup.getInstance();
        UpdateChecker checker = new UpdateChecker(plugin);

        plugin.getServer().getScheduler().runTask(plugin, new Runnable() {

            @Override
            public void run() {
                plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {

                    @Override
                    public void run() {
                        if (ConfigParser.getConfig().advanced.updateCheckEnabled) {
                            try {
                                new MessageUtil("Checking for updates...").toConsole(true).send();

                                currentVersion = checker.getCurrent();
                                latestVersion = checker.getLatest();

                                if (latestVersion > currentVersion) {
                                    new MessageUtil("Version 1." + latestVersion + " has been released." + " You are currently running version 1." + currentVersion).toConsole(true).send();
                                    new MessageUtil("Update at: http://dev.bukkit.org/bukkit-plugins/drivebackupv2/").toConsole(true).send();
                                } else if (currentVersion > latestVersion) {
                                    new MessageUtil("You are running an unsupported release!").toConsole(true).send();
                                    new MessageUtil("The recommended release is 1." + latestVersion + ", and you are running 1." + currentVersion).toConsole(true).send();
                                    new MessageUtil("If the plugin has just recently updated, please ignore this message").toConsole(true).send();
                                } else {
                                    new MessageUtil("Hooray! You are running the latest release!").toConsole(true).send();
                                }
                            } catch (UnknownHostException exception) {
                                new MessageUtil("There was an issue attempting to check for the latest DriveBackupV2 release, check your network connection").toPerm("drivebackup.linkAccounts").send();
                            } catch (Exception exception) {
                                new MessageUtil("There was an issue attempting to check for the latest DriveBackupV2 release").toConsole(true).send();
                                MessageUtil.sendConsoleException(exception);
                            }
                        }
                    }
                }, 0, SchedulerUtil.sToTicks(UPDATE_CHECK_INTERVAL));
            }
        });
    }

    /**
     * Gets whether an update is available for the plugin
     * @return whether an update is available
     */
    public static boolean isUpdateAvailable() {
        return latestVersion > currentVersion;
    } 

    private DriveBackup plugin;

    UpdateChecker(DriveBackup plugin) {
        this.plugin = plugin;
    };

    public double getCurrent() throws NumberFormatException {
        String versionTitle = plugin.getDescription().getVersion().split("-")[0];
        return Double.valueOf(versionTitle.replaceFirst("\\.", ""));
    }

    public double getLatest() throws IOException, NumberFormatException, UnknownHostException {
        Request request = new Request.Builder()
            .url("https://api.curseforge.com/servermods/files?projectids=" + BUKKIT_PROJECT_ID)
            .post(RequestBody.create("", null)) // Send empty request body
            .build();

        Response response = httpClient.newCall(request).execute();
        JSONArray pluginVersions = new JSONArray(response.body().string());
        response.close();

        if (pluginVersions.length() == 0) {
            throw new NumberFormatException();
        }

        String versionTitle = pluginVersions.getJSONObject(pluginVersions.length() - 1).getString("name").replace("DriveBackupV2-", "").trim();
        return Double.valueOf(versionTitle.replaceFirst("\\.", "").trim());
    }
}

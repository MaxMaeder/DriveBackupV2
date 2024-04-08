package ratismal.drivebackup.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.ConfigParser.Config;
import ratismal.drivebackup.config.configSections.BackupList;
import ratismal.drivebackup.config.configSections.BackupScheduling;
import ratismal.drivebackup.http.HttpClient;
import ratismal.drivebackup.plugin.DriveBackup;

import java.net.UnknownHostException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Deprecated
public class DebugCollector {
    private static final String PASTEBIN_UPLOAD_URL = "https://api.mclo.gs/1/log";

    private final String serverType;
    private final String serverVersion;
    private final boolean onlineMode;
    private final ConfigInfo configInfo;
    private final List<PluginInfo> plugins;
    private final RamInfo ramInfo;

    public DebugCollector(@NotNull DriveBackup plugin) {
        serverType = plugin.getServer().getName();
        serverVersion = plugin.getServer().getVersion();
        onlineMode = plugin.getServer().getOnlineMode();
        configInfo = new ConfigInfo();
        plugins = new ArrayList<>();
        ramInfo = new RamInfo();
        for (Plugin pinfo : plugin.getServer().getPluginManager().getPlugins()) {
            plugins.add(new PluginInfo(pinfo.getDescription().getName(), pinfo.getDescription().getVersion(), pinfo.getDescription().getMain(), pinfo.getDescription().getAuthors()));
        }
    }

    public String publish(DriveBackup plugin) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonInString = gson.toJson(this);
        RequestBody formBody = new FormBody.Builder()
            .add("content", jsonInString)
            .build();
        Request request = new Request.Builder()
            .url(PASTEBIN_UPLOAD_URL)
            .post(formBody)
            .build();
        try (Response response = HttpClient.getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Unexpected code " + response);
            }
            JSONObject responseJson = new JSONObject(response.body().string());
            return responseJson.getString("url");
        } catch (UnknownHostException e) {
            return "Network error, check your connection";
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed";
        }
    }

    private static class PluginInfo {
        private final String name;
        private final String version;
        private final String main;
        private final List<String> authors;
        private PluginInfo(String name2, String version2, String main2, List<String> authors2) {
            name = name2;
            version = version2;
            main = main2;
            authors = authors2;
        }
    }

    private static class ConfigInfo {
        private final boolean backupsRequirePlayers;
        private final boolean disableSavingDuringBackups;

        private final BackupScheduling scheduleBackups;
        private final BackupList backupList;

        private final boolean googleDriveEnabled;
        private final boolean oneDriveEnabled;
        private final boolean dropboxEnabled;
        private final boolean ftpEnabled;
        private final String ftpType;

        private final ZoneOffset timezone;

        private ConfigInfo() {
            Config config = ConfigParser.getConfig();
            backupsRequirePlayers = config.backupStorage.backupsRequirePlayers;
            disableSavingDuringBackups = config.backupStorage.disableSavingDuringBackups;
            scheduleBackups = config.backupScheduling;
            backupList = config.backupList;
            googleDriveEnabled = config.backupMethods.googleDrive.enabled;
            oneDriveEnabled = config.backupMethods.oneDrive.enabled;
            dropboxEnabled = config.backupMethods.dropbox.enabled;
            ftpEnabled = config.backupMethods.ftp.enabled;
            if (ftpEnabled) {
                if (config.backupMethods.ftp.sftp) {
                    ftpType = "SFTP";
                } else if (config.backupMethods.ftp.ftps) {
                    ftpType = "FTPS";
                } else {
                    ftpType = "FTP";
                }
            } else {
                ftpType = "none";
            }
            timezone = config.advanced.dateTimezone;
        }
    }

    private static class RamInfo {
        private static final long MEGABYTE = 1024L * 1024L;

        private final long free;
        private final long total;
        private final long max;

        private RamInfo() {
            free = Runtime.getRuntime().freeMemory() / MEGABYTE;
            total = Runtime.getRuntime().totalMemory() / MEGABYTE;
            max = Runtime.getRuntime().maxMemory() / MEGABYTE;
        }
    }
}

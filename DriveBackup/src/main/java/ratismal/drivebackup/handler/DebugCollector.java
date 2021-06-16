package ratismal.drivebackup.handler;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.plugin.Plugin;
import org.json.JSONObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.plugin.DriveBackup;

public class DebugCollector {

    private String serverType;
    private String serverVersion;
    private boolean onlineMode;
    private ConfigInfo configInfo;
    private List<PluginInfo> plugins;
    private RamInfo ramInfo;

    public DebugCollector(DriveBackup plugin) {
        this.serverType = plugin.getServer().getName();
        this.serverVersion = plugin.getServer().getVersion();
        this.onlineMode = plugin.getServer().getOnlineMode();
        this.configInfo = new ConfigInfo();
        this.plugins = new ArrayList<>();
        this.ramInfo = new RamInfo();

        for (Plugin pinfo : plugin.getServer().getPluginManager().getPlugins()) {
            this.plugins.add(new PluginInfo(pinfo.getDescription().getName(), pinfo.getDescription().getVersion(), pinfo.getDescription().getMain(), pinfo.getDescription().getAuthors()));
        }
    }

    public String publish(DriveBackup plugin) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonInString = gson.toJson(this);

        OkHttpClient httpClient = new OkHttpClient();

        RequestBody formBody = new FormBody.Builder()
            .add("content", jsonInString.toString())
            .build();


        Request request = new Request.Builder()
            .url("https://api.mclo.gs/1/log")
            .post(formBody)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new Exception("Unexpected code " + response);

            JSONObject responseJson = new JSONObject(response.body().string());
            String url = responseJson.getString("url");

            return url;
        } catch (Exception e) {
            e.printStackTrace();
            return "Failed";
        }
    }

    public static class PluginInfo {
        public String name;
        public String version;
        public String main;
        public List<String> authors;

        public PluginInfo(String name2, String version2, String main2, List<String> authors2) {
            this.name = name2;
            this.version = version2;
            this.main = main2;
            this.authors = authors2;
        }
    }

    public static class ConfigInfo {

        private final boolean backupsRequirePlayers;
        private final boolean disableSavingDuringBackups;

        private final boolean scheduleBackups;
        private final ZoneOffset backupScheduleTimezone;

        private final ZoneOffset backupFormatTimezone;
        private static ArrayList<HashMap<String, Object>> backupList;

        private final boolean googleDriveEnabled;
        private final boolean oneDriveEnabled;
        private final boolean dropboxEnabled;
        private final boolean ftpEnabled;
        private final String ftpType;

        ConfigInfo() {
            this.googleDriveEnabled = Config.isGoogleDriveEnabled();
            this.oneDriveEnabled = Config.isOneDriveEnabled();
            this.dropboxEnabled = Config.isDropboxEnabled();
            this.ftpEnabled = Config.isFtpEnabled();
            this.ftpType = Config.isFtpEnabled() ? (Config.isFtpFtps() ? "FTPS" : "SFTP") : "None";
            this.backupsRequirePlayers = Config.isBackupsRequirePlayers();
            this.disableSavingDuringBackups = Config.isSavingDisabledDuringBackups();
            this.scheduleBackups = Config.isBackupsScheduled();
            this.backupScheduleTimezone = Config.getBackupScheduleTimezone();
            this.backupFormatTimezone = Config.getBackupFormatTimezone();

        }
    }

    public static class RamInfo {

        private static final long MEGABYTE = 1024L * 1024L;
        private final long free;
        private final long total;
        private final long max;

        RamInfo() {
            this.free = Runtime.getRuntime().freeMemory() / MEGABYTE;
            this.total = Runtime.getRuntime().totalMemory() / MEGABYTE;
            this.max = Runtime.getRuntime().maxMemory() / MEGABYTE;
        }
    }
}
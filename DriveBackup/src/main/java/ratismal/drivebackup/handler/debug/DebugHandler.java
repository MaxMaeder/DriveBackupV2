package ratismal.drivebackup.handler.debug;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import ratismal.drivebackup.configuration.ConfigurationObject;
import ratismal.drivebackup.handler.logging.PrefixedLogger;
import ratismal.drivebackup.http.HttpClient;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DebugHandler {
    private static final String MC_LOGS_UPLOAD_URL = "https://api.mclo.gs/1/log";
    private static final List<String> SENSITIVE_CONFIG_KEYS = Arrays.asList("shared-drive-id", "hostname", "username", "password", "token", "sftp-public-key", "sftp-passphrase", "passphrase");
    private static final Pattern VALUE_PATTERN = Pattern.compile(":(\\s+)(.*)");
    private static final long MEGA_BYTE = 1024L * 1024L;
    private static final long SIZE_LIMIT = 10_000_000L;
    private static final int NUMBER_OF_LINES_LIMIT = 25_000;
    private final PrefixedLogger logger;
    private final DriveBackupInstance driveBackupInstance;
    
    @Contract (pure = true)
    public DebugHandler(@NotNull DriveBackupInstance instance) {
        driveBackupInstance = instance;
        logger = instance.getLoggingHandler().getPrefixedLogger("DebugHandler");
    }
    
    public String publishLatestLog() throws IOException {
        logger.info("Uploading latest.log");
        File latestLog = new File(driveBackupInstance.getDataDirectory().getAbsoluteFile().getParentFile().getParentFile() + "/logs/latest.log");
        long size = latestLog.length();
        logger.info("Checking size of latest log");
        if (SIZE_LIMIT < size) {
            logger.warn("File too large to upload: " + size);
            throw new IOException("File too large");
        }
        logger.info("Latest log size: " + size);
        List<String> lines = Files.readAllLines(latestLog.toPath(), StandardCharsets.UTF_8);
        logger.info("Checking line count of latest log");
        if (NUMBER_OF_LINES_LIMIT < lines.size()) {
            logger.warn("File contains too many line to upload: " + lines.size());
            throw new IOException("File too long");
        }
        logger.info("Latest log line count: " + lines.size());
        StringBuilder log = new StringBuilder(10_000);
        for (String line : lines) {
            log.append(line).append("\n");
        }
        return upload(log.toString(), "latest.log");
    }
    
    public String publishDebugInfo() throws IOException {
        String debugInfo = collectDebugInfo();
        return upload(debugInfo, "debug info");
    }
    
    private String upload(String content, String type) throws IOException {
        FormBody formBody = new FormBody.Builder()
                .add("content", content)
                .build();
        Request request = new Request.Builder()
                .url(MC_LOGS_UPLOAD_URL)
                .post(formBody)
                .build();
        logger.info("Making request to upload " + type);
        try (Response response = HttpClient.getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                final String REQUEST_FAILED = "Request failed with code: ";
                logger.warn(REQUEST_FAILED + response);
                throw new IOException(REQUEST_FAILED+ response);
            }
            ResponseBody body = response.body();
            if (body == null) {
                final String NULL_RESPONSE = "Response body is null";
                logger.warn(NULL_RESPONSE);
                throw new IOException(NULL_RESPONSE);
            }
            JSONObject responseJson = new JSONObject(body.string());
            logger.info("Successfully uploaded " + type + ", ID is: " + responseJson.getString("id"));
            return responseJson.getString("url");
        }
    }
    
    private @NotNull String collectDebugInfo() {
        logger.info("Collecting debug info");
        String serverInfo = driveBackupInstance.getServerInfo().getInfo();
        String systemInfo = getSystemInfo();
        String config = getConfig();
        logger.info("Building debug info");
        StringBuilder sb = new StringBuilder(10_000);
        sb.append("Debug Info\n");
        sb.append(serverInfo);
        sb.append("\n\n");
        sb.append(systemInfo);
        sb.append("\n\n");
        sb.append(config);
        sb.append("\n\n");
        logger.info("Finished building debug info");
        return sb.toString();
    }
    
    private @NotNull String getConfig() {
        logger.info("Getting config");
        ConfigurationObject config = driveBackupInstance.getConfigHandler().getConfig();
        File configFile;
        try {
            configFile = config.getConfigFile();
        } catch (IllegalStateException e) {
            final String FAILED_TO_GET_CONFIG = "Failed to get config file";
            logger.warn(FAILED_TO_GET_CONFIG, e);
            return FAILED_TO_GET_CONFIG;
        }
        logger.info("Reading config file");
        List<String> lines;
        try {
            lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            final String FAILED_TO_READ_CONFIG = "Failed to read config file";
            logger.warn(FAILED_TO_READ_CONFIG, e);
            return FAILED_TO_READ_CONFIG;
        }
        StringBuilder configString = new StringBuilder(1_000);
        configString.append("Config\n");
        configString.append("------\n");
        for (String line : lines) {
            String s = line;
            for (String key : SENSITIVE_CONFIG_KEYS) {
                if (s.contains(key)) {
                    String value = s.split(":")[1].trim();
                    Matcher matcher = VALUE_PATTERN.matcher(s);
                    if (value.isEmpty()) {
                        s = matcher.replaceAll(": <empty>");
                        break;
                    }
                    s = matcher.replaceAll(": <redacted>");
                    break;
                }
            }
            configString.append(s).append("\n");
        }
        configString.append("------\n");
        configString.append("End Config\n");
        return configString.toString();
    }
    
    private @NotNull String getSystemInfo() {
        logger.info("Getting system info");
        StringBuilder sb = new StringBuilder(1000);
        sb.append("System Info\n");
        sb.append("-----------\n");
        sb.append("Java Version: ").append(System.getProperty("java.version")).append("\n");
        sb.append("Java Vendor: ").append(System.getProperty("java.vendor")).append("\n");
        sb.append("Java VM Version: ").append(System.getProperty("java.vm.version")).append("\n");
        sb.append("Java VM Vendor: ").append(System.getProperty("java.vm.vendor")).append("\n");
        sb.append("OS Name: ").append(System.getProperty("os.name")).append("\n");
        sb.append("OS Version: ").append(System.getProperty("os.version")).append("\n");
        sb.append("OS Architecture: ").append(System.getProperty("os.arch")).append("\n");
        sb.append("Available Processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n");
        long max = Runtime.getRuntime().maxMemory() / MEGA_BYTE;
        long total = Runtime.getRuntime().totalMemory() / MEGA_BYTE;
        long free = Runtime.getRuntime().freeMemory() / MEGA_BYTE;
        sb.append("Max Memory MB: ").append(max).append("\n");
        sb.append("Total Memory MB: ").append(total).append("\n");
        sb.append("Free Memory MB: ").append(free).append("\n");
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        long uptime = runtime.getUptime();
        long hours = TimeUnit.MILLISECONDS.toHours(uptime);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(uptime) - TimeUnit.HOURS.toMinutes(hours);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(uptime) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(uptime));
        sb.append("Uptime: ").append(hours).append(" hours ").append(minutes).append(" minutes ").append(seconds).append(" seconds\n");
        sb.append("JVM Arguments: ").append("\n");
        List<String> arguments = runtime.getInputArguments();
        for (String argument : arguments) {
            sb.append(argument).append("\n");
        }
        sb.append("System Properties: ").append("\n");
        Map<String, String> systemProperties = runtime.getSystemProperties();
        sb.append("Total System Properties: ").append(systemProperties.size()).append("\n");
        for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("-----------\n");
        sb.append("End System Info\n");
        return sb.toString();
    }
    
}

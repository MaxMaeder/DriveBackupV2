package ratismal.drivebackup.uploaders;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import ratismal.drivebackup.UploadThread.UploadLogger;
import ratismal.drivebackup.handler.commandHandler.BasicCommands;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.uploaders.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.util.Logger;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.NetUtil;
import ratismal.drivebackup.util.SchedulerUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import static ratismal.drivebackup.config.Localization.intl;

public class Authenticator {
    /**
     * Endpoints
     */
    private static String AUTH_URL = "https://auth.drivebackupv2.com";
    private static String REQUEST_CODE_ENDPOINT = AUTH_URL + "/pin";
    private static String POLL_VERIFICATION_ENDPOINT = AUTH_URL + "/token";
    private static String ONEDRIVE_REQUEST_CODE_ENDPOINT = "https://login.microsoftonline.com/common/oauth2/v2.0/devicecode";
    private static String ONEDRIVE_POLL_VERIFICATION_ENDPOINT = "https://login.microsoftonline.com/common/oauth2/v2.0/token";

    /**
     * Authenticator client secret
     */
    private static String CLIENT_SECRET = "fyKCRZRyJeHW5PzGJvQkL4dr2zRHRmwTaOutG7BBhQM=";

    private static int taskId = -1;

    public enum AuthenticationProvider {
        GOOGLE_DRIVE("Google Drive", "googledrive", "/GoogleDriveCredential.json", "qWd2xXC/ORzdZvUotXoWhHC0POkMNuO/xuwcKWc9s1LLodayZXvkdKimmpOQqWYS6I+qGSrYNb8UCJWMhrgDXhIWEbDvytkQTwq+uNcnfw8=", "pasQz0KvtyC7o6CrlLPSMVV9Y0RMX76cXzsAbBoCBxI="),
        ONEDRIVE("OneDrive", "onedrive", "/OneDriveCredential.json", "Ktj7Jd1h0oYNVicuyTBk5fU+gHS+QYReZxZKNZNO9CDxxHaf8bXlw0SKO9jnwc81", ""),
        DROPBOX("Dropbox", "dropbox", "/DropboxCredential.json", "OSpqXymVUFSRnANAmj2DTA==", "4MrYNbN0I6J/fsAFeF00GQ==");

        private final String name;
        private final String id;
        private final String credStoreLocation;
        private final String clientId;
        private final String clientSecret;

        AuthenticationProvider(String name, String id, String credStoreLocation, String clientId, String clientSecret) {
            this.name = name;
            this.id = id;
            this.credStoreLocation = credStoreLocation;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        public String getName() {
            return name;
        }

        public String getId() {
            return id;
        }

        public @NotNull String getCredStoreLocation() {
            return DriveBackup.getInstance().getDataFolder().getAbsolutePath() + credStoreLocation;
        }

        public String getClientId() {
            return clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }
    }

    /**
     * Attempt to authenticate a user with the specified authentication provider 
     * using the OAuth 2.0-device authorization grant flow.
     * 
     * @param provider an {@code AuthenticationProvider}
     * @param initiator user who initiated the authentication
     */
    public static void authenticateUser(final AuthenticationProvider provider, final CommandSender initiator) {
        DriveBackup plugin = DriveBackup.getInstance();
        Logger logger = (input, placeholders) -> MessageUtil.Builder().mmText(input, placeholders).to(initiator).toConsole(false).send();
        cancelPollTask();
        try {
            FormBody.Builder requestBody = new FormBody.Builder()
                .add("type", provider.getId());
            String requestEndpoint;
            if (provider == AuthenticationProvider.ONEDRIVE) {
                requestBody.add("client_id", Obfusticate.decrypt(provider.getClientId()));
                requestBody.add("scope", "offline_access Files.ReadWrite");
                requestEndpoint = ONEDRIVE_REQUEST_CODE_ENDPOINT;
            } else {
                requestBody.add("client_secret", Obfusticate.decrypt(CLIENT_SECRET));
                requestEndpoint = REQUEST_CODE_ENDPOINT;
            }
            Request request = new Request.Builder()
                .url(requestEndpoint)
                .post(requestBody.build())
                .build();
            Response response = DriveBackup.httpClient.newCall(request).execute();
            JSONObject parsedResponse = new JSONObject(response.body().string());
            response.close();
            String userCode = parsedResponse.getString("user_code");
            String deviceCode = parsedResponse.getString("device_code");
            String verificationUri = parsedResponse.getString("verification_uri");
            long responseCheckDelay = SchedulerUtil.sToTicks(parsedResponse.getLong("interval"));
            logger.log(
                intl("link-account-code"),
                "link-url", verificationUri,
                "link-code", userCode,
                "provider", provider.getName());
            taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
                @Override
                public void run() {
                    try {
                        FormBody.Builder requestBody = new FormBody.Builder()
                            .add("device_code", deviceCode)
                            .add("user_code", userCode);
                        String requestEndpoint;
                        if (provider == AuthenticationProvider.ONEDRIVE) {
                            requestBody.add("client_id", Obfusticate.decrypt(provider.getClientId()));
                            requestBody.add("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
                            requestEndpoint = ONEDRIVE_POLL_VERIFICATION_ENDPOINT;
                        } else {
                            requestBody.add("client_secret", Obfusticate.decrypt(CLIENT_SECRET));
                            requestEndpoint = POLL_VERIFICATION_ENDPOINT;
                        }
                        Request request = new Request.Builder()
                            .url(requestEndpoint)
                            .post(requestBody.build())
                            .build();
                        Response response = DriveBackup.httpClient.newCall(request).execute();
                        JSONObject parsedResponse = new JSONObject(response.body().string());
                        response.close();
                        if (parsedResponse.has("refresh_token")) {
                            saveRefreshToken(provider, (String) parsedResponse.get("refresh_token"));
                            if (provider.getId() == "googledrive") {
                                UploadLogger uploadLogger = new UploadLogger() {
                                    @Override
                                    public void log(String input, String... placeholders) {
                                        MessageUtil.Builder()
                                                .mmText(input, placeholders)
                                                .to(initiator)
                                                .send();
                                    }
                                };
                                new GoogleDriveUploader(uploadLogger).setupSharedDrives(initiator);
                            } else {
                                linkSuccess(initiator, provider, logger);
                            }
                            cancelPollTask();
                        } else if (
                            (provider == AuthenticationProvider.ONEDRIVE && !parsedResponse.getString("error").equals("authorization_pending")) ||
                            (provider != AuthenticationProvider.ONEDRIVE && !parsedResponse.get("msg").equals("code_not_authenticated"))
                            ) {
                            MessageUtil.Builder().text(parsedResponse.toString()).send();
                            throw new UploadException();
                        }
                    } catch (Exception exception) {
                        NetUtil.catchException(exception, AUTH_URL, logger);
                        logger.log(intl("link-provider-failed"), "provider", provider.getName());
                        MessageUtil.sendConsoleException(exception);
                        cancelPollTask();
                    }
                }
            }, responseCheckDelay, responseCheckDelay);
        } catch (Exception exception) {
            NetUtil.catchException(exception, AUTH_URL, logger);
            logger.log(intl("link-provider-failed"), "provider", provider.getName());
            MessageUtil.sendConsoleException(exception);
        }
    }

    public static void unauthenticateUser(final AuthenticationProvider provider, final CommandSender initiator) {
        Logger logger = (input, placeholders) -> MessageUtil.Builder().mmText(input, placeholders).to(initiator).send();
        disableBackupMethod(provider, logger);
        try {
            File credStoreFile = new File(provider.getCredStoreLocation());
            if (credStoreFile.exists()) {
                credStoreFile.delete();
            }
        } catch (Exception exception) {
            logger.log(intl("unlink-provider-failed"), "provider", provider.getName());
            MessageUtil.sendConsoleException(exception);
        }
        logger.log(intl("unlink-provider-complete"), "provider", provider.getName());
    }

    private static void cancelPollTask() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public static void linkSuccess(CommandSender initiator, @NotNull AuthenticationProvider provider, @NotNull Logger logger) {
        logger.log(intl("link-provider-complete"), "provider", provider.getName());
        enableBackupMethod(provider, logger);
        DriveBackup.reloadLocalConfig();
        BasicCommands.sendBriefBackupList(initiator);
    }

    private static void saveRefreshToken(@NotNull AuthenticationProvider provider, String token) throws Exception {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("refresh_token", token);
        FileWriter file = new FileWriter(provider.getCredStoreLocation());
        file.write(jsonObject.toString());
        file.close();
    }

    private static void enableBackupMethod(@NotNull AuthenticationProvider provider, Logger logger) {
        DriveBackup plugin = DriveBackup.getInstance();
        if (!plugin.getConfig().getBoolean(provider.getId() + ".enabled")) {
            logger.log("Automatically enabled " + provider.getName() + " backups");
            plugin.getConfig().set(provider.getId() + ".enabled", true);
            plugin.saveConfig();
        }
    }

    private static void disableBackupMethod(@NotNull AuthenticationProvider provider, Logger logger) {
        DriveBackup plugin = DriveBackup.getInstance();
        if (plugin.getConfig().getBoolean(provider.getId() + ".enabled")) {
            logger.log("Disabled " + provider.getName() + " backups");
            plugin.getConfig().set(provider.getId() + ".enabled", false);
            plugin.saveConfig();
        }
    }

    @NotNull
    public static String getRefreshToken(AuthenticationProvider provider) {
        try {
            String clientJSON = processCredentialJsonFile(provider);
            JSONObject clientJsonObject = new JSONObject(clientJSON);
            String readRefreshToken = (String) clientJsonObject.get("refresh_token");
            if (readRefreshToken == null || readRefreshToken.isEmpty()) {
                throw new Exception();
            }
            return readRefreshToken;
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean hasRefreshToken(AuthenticationProvider provider) {
        // what am I doing with my life?
        return !getRefreshToken(provider).isEmpty();
    }

    @NotNull
    private static String processCredentialJsonFile(@NotNull AuthenticationProvider provider) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(provider.getCredStoreLocation()));) {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                line = br.readLine();
            }
            return sb.toString();
        }
    }
}

package ratismal.drivebackup.uploaders;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import ratismal.drivebackup.configuration.ConfigHandler;
import ratismal.drivebackup.configuration.ConfigurationSection;
import ratismal.drivebackup.constants.Initiator;
import ratismal.drivebackup.handler.task.TaskIdentifier;
import ratismal.drivebackup.http.HttpClient;
import ratismal.drivebackup.objects.Player;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.uploaders.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.util.NetUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class Authenticator {
    /**
     * Endpoints
     */
    private static final String AUTH_URL = "https://auth.drivebackupv2.com";
    private static final String REQUEST_CODE_ENDPOINT = AUTH_URL + "/pin";
    private static final String POLL_VERIFICATION_ENDPOINT = AUTH_URL + "/token";
    private static final String ONEDRIVE_REQUEST_CODE_ENDPOINT = "https://login.microsoftonline.com/common/oauth2/v2.0/devicecode";
    private static final String ONEDRIVE_POLL_VERIFICATION_ENDPOINT = "https://login.microsoftonline.com/common/oauth2/v2.0/token";

    /**
     * Authenticator client secret
     */
    private static final String CLIENT_SECRET = "fyKCRZRyJeHW5PzGJvQkL4dr2zRHRmwTaOutG7BBhQM=";

    private static TaskIdentifier taskId;
    
    @Contract (pure = true)
    private Authenticator() {}
    
    /**
     * Attempt to authenticate a user with the specified authentication provider 
     * using the OAuth 2.0-device authorization grant flow.
     * 
     * @param provider an {@code AuthenticationProvider}
     * @param player user who initiated the authentication
     */
    public static void authenticateUser(AuthenticationProvider provider, Player player, DriveBackupInstance instance) {
        UploadLogger logger = new UploadLogger(instance, Initiator.OTHER);
        cancelPollTask(instance);
        try {
            FormBody.Builder requestBody = new FormBody.Builder()
                .add("type", provider.getId());
            String requestEndpoint;
            if (AuthenticationProvider.ONEDRIVE == provider) {
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
            Response response = HttpClient.getHttpClient().newCall(request).execute();
            ResponseBody body = response.body();
            if (body == null) {
                return;
            }
            JSONObject parsedResponse = new JSONObject(body.string());
            response.close();
            String userCode = parsedResponse.getString("user_code");
            String deviceCode = parsedResponse.getString("device_code");
            String verificationUri = parsedResponse.getString("verification_uri");
            long responseCheckDelay = parsedResponse.getLong("interval");
            Map<String, String> placeholders = new HashMap<>(3);
            placeholders.put("link-url", verificationUri);
            placeholders.put("link-code", userCode);
            placeholders.put("provider", provider.getName());
            logger.log("link-account-code", placeholders);
            taskId = instance.getTaskHandler().scheduleSyncRepeatingTask(() -> {
                try {
                    FormBody.Builder requestBody1 = new FormBody.Builder()
                        .add("device_code", deviceCode)
                        .add("user_code", userCode);
                    String requestEndpoint1;
                    if (AuthenticationProvider.ONEDRIVE == provider) {
                        requestBody1.add("client_id", Obfusticate.decrypt(provider.getClientId()));
                        requestBody1.add("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
                        requestEndpoint1 = ONEDRIVE_POLL_VERIFICATION_ENDPOINT;
                    } else {
                        requestBody1.add("client_secret", Obfusticate.decrypt(CLIENT_SECRET));
                        requestEndpoint1 = POLL_VERIFICATION_ENDPOINT;
                    }
                    Request request1 = new Request.Builder()
                        .url(requestEndpoint1)
                        .post(requestBody1.build())
                        .build();
                    Response response1 = HttpClient.getHttpClient().newCall(request1).execute();
                    ResponseBody body1 = response1.body();
                    if (body1 == null) {
                        logger.log("Body returned null from " + requestEndpoint1);
                        return;
                    }
                    JSONObject parsedResponse1 = new JSONObject(body1.string());
                    response1.close();
                    if (parsedResponse1.has("refresh_token")) {
                        saveRefreshToken(provider, (String) parsedResponse1.get("refresh_token"));
                        if (provider.getId().equals("googledrive")) {
                            UploadLogger uploadLogger = new UploadLogger(instance, Initiator.OTHER);
                            new GoogleDriveUploader(instance, uploadLogger).setupSharedDrives(player);
                        } else {
                            linkSuccess(player, provider, logger, instance);
                        }
                        cancelPollTask(instance);
                    } else if (
                            (AuthenticationProvider.ONEDRIVE == provider && !parsedResponse1.getString("error").equals("authorization_pending")) ||
                            (provider != AuthenticationProvider.ONEDRIVE && !parsedResponse1.get("msg").equals("code_not_authenticated"))
                        ) {
                        logger.log(parsedResponse1.toString());
                        throw new AuthException();
                    }
                } catch (Exception exception) {
                    NetUtil.catchException(exception, AUTH_URL, logger);
                    logger.log("link-provider-failed", "provider", provider.getName());
                    cancelPollTask(instance);
                }
            }, responseCheckDelay, responseCheckDelay, TimeUnit.SECONDS);
        } catch (Exception exception) {
            NetUtil.catchException(exception, AUTH_URL, logger);
            logger.log("link-provider-failed", "provider", provider.getName());
            instance.getLoggingHandler().error("Failed to authenticate user with " + provider.getName() + " provider", exception);
        }
    }

    public static void unAuthenticateUser(AuthenticationProvider provider, Player player, DriveBackupInstance instance) {
        UploadLogger logger;
        if (player == null) {
            logger = new UploadLogger(instance, Initiator.OTHER);
        } else {
            logger = new UploadLogger(instance, player);
        }
        disableBackupMethod(provider, logger, instance);
        try {
            File credStoreFile = new File(provider.getCredStoreLocation());
            if (credStoreFile.exists()) {
                credStoreFile.delete();
            }
        } catch (Exception exception) {
            logger.log("unlink-provider-failed", "provider", provider.getName());
            logger.log("Failed to unlink ", exception);
        }
        logger.log("unlink-provider-complete", "provider", provider.getName());
    }

    private static void cancelPollTask(DriveBackupInstance instance) {
        if (taskId != null) {
            instance.getTaskHandler().cancelTask(taskId);
        }
    }

    public static void linkSuccess(Player player, @NotNull AuthenticationProvider provider, @NotNull UploadLogger logger, DriveBackupInstance instance) {
        logger.log("link-provider-complete", "provider", provider.getName());
        enableBackupMethod(provider, logger, instance);
        //TODO send backup list
        //BasicCommands.sendBriefBackupList(initiator);
    }

    public static void saveRefreshToken(@NotNull AuthenticationProvider provider, String token) throws Exception {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("refresh_token", token);
        try (FileWriter file = new FileWriter(provider.getCredStoreLocation())) {
            file.write(jsonObject.toString());
        }
    }

    private static void enableBackupMethod(@NotNull AuthenticationProvider provider, UploadLogger logger, DriveBackupInstance instance) {
        ConfigHandler configHandler = instance.getConfigHandler();
        ConfigurationSection section = configHandler.getConfig().getSection(provider.getId());
        if (!section.getValue("enabled").getBoolean()) {
            try {
                configHandler.getConfig().getConfig().node(provider.getId()).node("enabled").set(Boolean.TRUE);
                configHandler.save();
                logger.log("Automatically enabled " + provider.getName() + " backups");
            } catch (Exception e) {
                logger.log("Failed to enable " + provider.getName() + " backups");
                instance.getLoggingHandler().error("Failed to enable " + provider.getName() + " backups", e);
            }
        }
    }

    private static void disableBackupMethod(@NotNull AuthenticationProvider provider, UploadLogger logger, DriveBackupInstance instance) {
        ConfigHandler configHandler = instance.getConfigHandler();
        ConfigurationSection section = configHandler.getConfig().getSection(provider.getId());
        if (section.getValue("enabled").getBoolean()) {
            try {
                configHandler.getConfig().getConfig().node(provider.getId()).node("enabled").set(Boolean.FALSE);
                configHandler.save();
                logger.log("Automatically disabled " + provider.getName() + " backups");
            } catch (Exception e) {
                logger.log("Failed to disable " + provider.getName() + " backups");
                instance.getLoggingHandler().error("Failed to disable " + provider.getName() + " backups", e);
            }
        }
    }

    @NotNull
    public static String getRefreshToken(AuthenticationProvider provider) {
        try {
            String clientJSON = processCredentialJsonFile(provider);
            JSONObject clientJsonObject = new JSONObject(clientJSON);
            String readRefreshToken = (String) clientJsonObject.get("refresh_token");
            if (readRefreshToken == null || readRefreshToken.isEmpty()) {
                return "";
            }
            return readRefreshToken;
        } catch (IOException | JSONException e) {
            return "";
        }
    }

    public static boolean hasRefreshToken(AuthenticationProvider provider) {
        // what am I doing with my life?
        return !getRefreshToken(provider).isEmpty();
    }

    @NotNull
    private static String processCredentialJsonFile(@NotNull AuthenticationProvider provider) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(provider.getCredStoreLocation()))) {
            StringBuilder sb = new StringBuilder(1_000);
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                line = br.readLine();
            }
            return sb.toString();
        }
    }
}

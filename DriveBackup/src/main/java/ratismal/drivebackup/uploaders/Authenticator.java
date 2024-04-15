package ratismal.drivebackup.uploaders;

import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import ratismal.drivebackup.UploadThread.UploadLogger;
import ratismal.drivebackup.handler.commandHandler.BasicCommands;
import ratismal.drivebackup.http.HttpClient;
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

    private static int taskId = -1;
    
    @Contract (pure = true)
    private Authenticator() {}
    
    /**
     * Attempt to authenticate a user with the specified authentication provider 
     * using the OAuth 2.0-device authorization grant flow.
     * 
     * @param provider an {@code AuthenticationProvider}
     * @param initiator user who initiated the authentication
     */
    public static void authenticateUser(AuthenticationProvider provider, CommandSender initiator) {
        DriveBackup plugin = DriveBackup.getInstance();
        Logger logger = (input, placeholders) -> MessageUtil.Builder().mmText(input, placeholders).to(initiator).toConsole(false).send();
        cancelPollTask();
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
            long responseCheckDelay = SchedulerUtil.sToTicks(parsedResponse.getLong("interval"));
            logger.log(
                intl("link-account-code"),
                "link-url", verificationUri,
                "link-code", userCode,
                "provider", provider.getName());
            taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
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
                    JSONObject parsedResponse1 = new JSONObject(response1.body().string());
                    response1.close();
                    if (parsedResponse1.has("refresh_token")) {
                        saveRefreshToken(provider, (String) parsedResponse1.get("refresh_token"));
                        if (provider.getId().equals("googledrive")) {
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
                            (AuthenticationProvider.ONEDRIVE == provider && !parsedResponse1.getString("error").equals("authorization_pending")) ||
                            (provider != AuthenticationProvider.ONEDRIVE && !parsedResponse1.get("msg").equals("code_not_authenticated"))
                        ) {
                        MessageUtil.Builder().text(parsedResponse1.toString()).send();
                        throw new UploadException();
                    }
                } catch (Exception exception) {
                    NetUtil.catchException(exception, AUTH_URL, logger);
                    logger.log(intl("link-provider-failed"), "provider", provider.getName());
                    MessageUtil.sendConsoleException(exception);
                    cancelPollTask();
                }
            }, responseCheckDelay, responseCheckDelay);
        } catch (Exception exception) {
            NetUtil.catchException(exception, AUTH_URL, logger);
            logger.log(intl("link-provider-failed"), "provider", provider.getName());
            MessageUtil.sendConsoleException(exception);
        }
    }

    public static void unAuthenticateUser(AuthenticationProvider provider, CommandSender initiator) {
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
        if (-1 != taskId) {
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
        try (FileWriter file = new FileWriter(provider.getCredStoreLocation())) {
            file.write(jsonObject.toString());
        }
    }

    private static void enableBackupMethod(@NotNull AuthenticationProvider provider, Logger logger) {
        DriveBackup plugin = DriveBackup.getInstance();
        if (!plugin.getConfig().getBoolean(provider.getId() + ".enabled")) {
            logger.log("Automatically enabled " + provider.getName() + " backups");
            plugin.getConfig().set(provider.getId() + ".enabled", Boolean.TRUE);
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

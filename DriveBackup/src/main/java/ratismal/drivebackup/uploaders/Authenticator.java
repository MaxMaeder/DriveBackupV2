package ratismal.drivebackup.uploaders;

import org.bukkit.command.CommandSender;
import org.json.JSONObject;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ratismal.drivebackup.UploadThread.UploadLogger;
import ratismal.drivebackup.config.Permissions;
import ratismal.drivebackup.handler.commandHandler.BasicCommands;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.uploaders.googledrive.GoogleDriveUploader;
import ratismal.drivebackup.util.Logger;
import ratismal.drivebackup.util.MessageUtil;
import ratismal.drivebackup.util.SchedulerUtil;

import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.UnknownHostException;

import static ratismal.drivebackup.config.Localization.intl;

public class Authenticator {
    /**
     * Endpoints
     */
    private static String REQUEST_CODE_ENDPOINT = "https://drivebackup.web.app/pin";
    private static String VERIFICATION_ENDPOINT = "https://drivebackup.web.app/";
    private static String POLL_VERIFICATION_ENDPOINT = "https://drivebackup.web.app/token";

    /**
     * Global instance of the HTTP client
     */
    private static final OkHttpClient httpClient = new OkHttpClient();

    public enum AuthenticationProvider {
        GOOGLE_DRIVE("Google Drive", "googledrive", "/GoogleDriveCredential.json"),
        ONEDRIVE("OneDrive", "onedrive", "/OneDriveCredential.json"),
        DROPBOX("Dropbox", "dropbox", "/DropboxCredential.json");

        private final String name;
        private final String id;
        private final String credStoreLocation;

        AuthenticationProvider(final String name, final String id, final String credStoreLocation) {
            this.name = name;
            this.id = id;
            this.credStoreLocation = credStoreLocation;
        }

        public String getName() {
            return name;
        }

        public String getId() {
            return id;
        }

        public String getCredStoreLocation() {
            return DriveBackup.getInstance().getDataFolder().getAbsolutePath() + credStoreLocation;
        }
    }

    /**
     * Attempt to authenticate a user with the specified authentication provider 
     * using the OAuth 2.0 device authorization grant flow
     * 
     * @param provider an {@code AuthenticationProvider}
     * @param initiator user who initiated the authentication
     */
    public static void authenticateUser(final AuthenticationProvider provider, final CommandSender initiator) {
        DriveBackup plugin = DriveBackup.getInstance();

        Logger logger = (input, placeholders) -> {
            MessageUtil.Builder().mmText(input, placeholders).to(initiator).toConsole(false).send();
        };

        try {
            RequestBody requestBody = new FormBody.Builder()
                .add("type", provider.getId())
                .build();

            Request request = new Request.Builder()
                .url(REQUEST_CODE_ENDPOINT)
                .post(requestBody)
                .build();

            Response response = httpClient.newCall(request).execute();
            JSONObject parsedResponse = new JSONObject(response.body().string());
            response.close();

            String userCode = parsedResponse.getString("user_code");
            final String deviceCode = parsedResponse.getString("device_code");
            long responseCheckDelay = SchedulerUtil.sToTicks(parsedResponse.getLong("interval"));

            logger.log(
                intl("link-account-code")
                    .replace("link-url", VERIFICATION_ENDPOINT)
                    .replace("link-code", userCode),
                "provider", provider.getName());

            final int[] task = new int[]{-1};
            task[0] = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
                @Override
                public void run() {
                    try {
                        RequestBody requestBody = new FormBody.Builder()
                            .add("device_code", deviceCode)
                            .add("user_code", userCode)
                            .build();
                
                        Request request = new Request.Builder()
                            .url(POLL_VERIFICATION_ENDPOINT)
                            .post(requestBody)
                            .build();
                        
                        Response response = httpClient.newCall(request).execute();
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
                                Authenticator.linkSuccess(initiator, provider, logger);
                            }

                            Bukkit.getScheduler().cancelTask(task[0]);

                        } else if (!parsedResponse.get("msg").equals("code_not_authenticated")) {
                            MessageUtil.Builder().text(parsedResponse.toString()).send();
                            throw new UploadException();
                        }

                    } catch (UnknownHostException exception) {
                        logger.log("Failed to link your " + provider.getName() + " account, check your network connection");   
                        
                        Bukkit.getScheduler().cancelTask(task[0]);

                    } catch (Exception exception) {
                        Authenticator.linkFail(provider, logger, exception);

                        Bukkit.getScheduler().cancelTask(task[0]);
                    }
                }
            }, responseCheckDelay, responseCheckDelay);
        } catch (UnknownHostException exception) {
            logger.log("Failed to link your " + provider.getName() + " account, check your network connection");
            
        } catch (Exception exception) {
            Authenticator.linkFail(provider, logger, exception);
        }
    }

    public static void linkSuccess(CommandSender initiator, AuthenticationProvider provider, Logger logger) {
        logger.log("Your " + provider.getName() + " account is linked!");

        enableBackupMethod(provider, logger);

        DriveBackup.reloadLocalConfig();

        BasicCommands.sendBriefBackupList(initiator);
    }

    public static void linkFail(AuthenticationProvider provider, Logger logger) {
        logger.log("Failed to link your " + provider.getName() + " account, please try again");
    }

    public static void linkFail(AuthenticationProvider provider, Logger logger, Exception exception) {
        logger.log("Failed to link your " + provider.getName() + " account, please try again");

        MessageUtil.sendConsoleException(exception);
    }

    private static void saveRefreshToken(AuthenticationProvider provider, String token) throws Exception {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("refresh_token", token);

        FileWriter file = new FileWriter(provider.getCredStoreLocation());
        file.write(jsonObject.toString());
        file.close();
    }

    private static void enableBackupMethod(AuthenticationProvider provider, Logger logger) {
        DriveBackup plugin = DriveBackup.getInstance();

        if (!plugin.getConfig().getBoolean(provider.getId() + ".enabled")) {
            logger.log("Automatically enabled " + provider.getName() + " backups");
            plugin.getConfig().set(provider.getId() + ".enabled", true);
            plugin.saveConfig();
        }
    }

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
        // what am i doing with my life
        return !getRefreshToken(provider).isEmpty();
    }

    /*public static String getAccessToken(AuthenticationProvider provider) {

    }*/

    private static String processCredentialJsonFile(AuthenticationProvider provider) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(provider.getCredStoreLocation()));
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();

        while (line != null) {
            sb.append(line);
            line = br.readLine();
        }
        
        String result = sb.toString();
        br.close(); 

        return result;
    }
}

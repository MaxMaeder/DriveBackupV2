package ratismal.drivecredentials;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;
import org.json.JSONObject;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Created by Ratismal on 2016-01-21.
 */

public class DriveCredentials {

    //Variables
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();
    private static final java.io.File DATA_STORE_DIR = new java.io.File(
            System.getProperty("user.dir"));
    private static FileDataStoreFactory DATA_STORE_FACTORY;
    private static HttpTransport httpTransport = new NetHttpTransport();


    public static void main(String[] arguments) {
        Console c = System.console();
        System.out.println("Hello! This is a simple program to generate a StoredCredential file.");
        System.out.println("You'll see a webpage pop up shortly. All you need to do is click 'allow'.");
        System.out.println("Naturally, you need to have a UI OS with a web browser to do this. ");
        System.out.println("This program is completely open source, so you can make sure that I'm not doing");
        System.out.println("anything naughty with it!");
        System.out.println("To open the repo, type 'y' now. Type anything else to continue.");
        String line = c.readLine();
        if (line.equalsIgnoreCase("y")) {
            System.out.println("Opening github repo. Enter anything to continue.");
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(new URI("https://github.com/MaxMaeder/DriveBackup"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            c.readLine();
        }
        boolean canContinue = false;

        while (!canContinue) {
            System.out.println("OK! Let's begin.");
            System.out.println("First of all, type the number of the credential you wish to generate.");
            System.out.println("   1. Google Drive");
            System.out.println("   2. OneDrive");
            System.out.println("   3. Both");
            String input = c.readLine();
            switch (input) {
                case "1":
                    System.out.println("We're generating Google Drive credentials!");
                    try {
                        authorizeGoogle();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    canContinue = true;
                    break;
                case "2":
                    System.out.println("We're generating OneDrive credentials!");
                    try {
                        authorizeOnedrive();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    canContinue = true;
                    break;
                case "3":
                    System.out.println("We're generating both Google Drive and OneDrive credentials!");
                    canContinue = true;
                    break;
                default:
                    System.out.println("Whoops! Didn't quite understand what you meant by that. Let's try again!");
                    break;
            }
        }
        System.out.println("And that should be it! Hopefully everything worked ok!");
        System.out.println("Type anything to exit.");
        c.readLine();
    }

    public static void authorizeOnedrive() throws URISyntaxException, IOException {
        Desktop.getDesktop().browse(new URI("https://login.live.com/oauth20_authorize.srf?client_id=0000000044" +
                "17D081&scope=wl.signin%20wl.basic%20wl.offline_access%20wl.skydrive_update&response_type=code"));
        System.out.println("After accepting, you will be redirected to a webpage.");
        System.out.println("Please copy and paste it's URL here.");
        Console c = System.console();
        String newUrl = c.readLine();

        String code = newUrl.split(Pattern.quote("?code="))[1];

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", code);
        jsonObject.put("auth_key", "");
        jsonObject.put("refresh_key", "");


        try {
            FileWriter file = new FileWriter("OneDriveCredential.json");
            file.write(jsonObject.toString());
            file.close();
            System.out.println("\nJSON Object: " + jsonObject);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Credential authorizeGoogle() throws IOException {
        // Load client secrets.
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(DriveCredentials.class.getResourceAsStream("/google_client_secrets.json")));

        DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        httpTransport, JSON_FACTORY, clientSecrets, Arrays.asList(DriveScopes.DRIVE))
                        .setDataStoreFactory(DATA_STORE_FACTORY)
                        .setAccessType("offline")
                        .build();
        Credential credential = new AuthorizationCodeInstalledApp(
                flow, new LocalServerReceiver()).authorize("user");
        System.out.println(
                "Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }
}
package ratismal.drivebackup.net;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import org.mortbay.util.ajax.JSON;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.util.MessageUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class Uploader {

    private static final String APPLICATION_NAME = "";

    private static final String UPLOAD_FILE_PATH = "Enter File Path";
    private static final String DIR_FOR_DOWNLOADS = "Enter Download Directory";
    //private static final java.io.File UPLOAD_FILE = new java.io.File(UPLOAD_FILE_PATH);

    /**
     * Directory to store user credentials.
     */

    private static String REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob";
    /**
     * Global instance of the {link DataStoreFactory}. The best practice is to make it a single
     * globally shared instance across your application.
     */
    private static FileDataStoreFactory dataStoreFactory;

    private static GoogleAuthorizationCodeFlow flow;

    /**
     * Global instance of the HTTP transport.
     */
    private static HttpTransport httpTransport = new NetHttpTransport();

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private static final java.io.File DATA_STORE_DIR = new java.io.File(
            DriveBackup.getInstance().getDataFolder().getAbsolutePath());

    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /**
     * Global Drive API client.
     */
    private static Drive drive;
    private static boolean goodToGo = false;

    private static Credential credential;

    public static Credential authorize() throws IOException {
        // Load client secrets.
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(DriveBackup.getInstance().getResource("client_secrets.json")));

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

    public static Drive getDriveService() throws IOException {
        Credential credential = authorize();
        return new Drive.Builder(
                httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }


    public static void uploadFile(java.io.File file, boolean useDirectUpload) throws IOException {
        Drive service = getDriveService();

        File body = new File();
        body.setTitle(file.getName());
        body.setDescription("DriveBackup plugin");
        body.setMimeType("application/zip");
        //if (destination != null && destination.length() > 0) {
        //    body.setParents(Arrays.asList(new ParentReference().setId(destination)));
        //}
        String destination = Config.getDestination();

        java.io.File fileContent = file;
        FileContent mediaContent = new FileContent("application/zip", fileContent);
        Drive.Files.List request = service.files().list().setQ(
                "mimeType='application/vnd.google-apps.folder' and trashed=false");
        FileList files = request.execute();
        boolean isFolder = false;
        String parentId = "null";
        for (File folderfiles : files.getItems()) {
            //System.out.println(folderfiles.getTitle());
            if (folderfiles.getTitle().equals(destination)) {
                parentId = folderfiles.getId();
                isFolder = true;
            }
        }
        if (!isFolder) {
            System.out.println("Creating destination folder");
            File folder = new File();
            folder.setTitle(destination);
            folder.setMimeType("application/vnd.google-apps.folder");
            File file3 = service.files().insert(folder).execute();
            parentId = file3.getId();
        }
        //if (service.files().get())
        //service.files().
        ParentReference newParent = new ParentReference();
        newParent.setId(parentId);
        body.setParents(Arrays.asList(newParent));

        File file2;
        try {
            file2 = service.files().insert(body, mediaContent).execute();
            //file2 = service.parents().insert(body, newParent).execute();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    public static boolean isGoodToGo() {
        return goodToGo;
    }


}

package ratismal.drivebackup.googledrive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.*;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.util.MessageUtil;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class GoogleUploader {

    private static final String APPLICATION_NAME = "DriveBackup";

    /**
     * Global instance of the HTTP transport.
     */
    private static final HttpTransport httpTransport = new NetHttpTransport();

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private static final java.io.File DATA_STORE_DIR = new java.io.File(
            DriveBackup.getInstance().getDataFolder().getAbsolutePath());

    private static FileDataStoreFactory DATA_STORE_FACTORY;

    private static final String CLIENT_ID = "848896104658-shap5e212clkamtac4lrjvledm0ni1hl.apps.googleusercontent.com";
    private static final String CLIENT_SECRET = "hIdUmBNGRGRiV5wVAC65ES0Y";

    /**
     * Global Drive API client.
     */
    private static Credential authorize() throws IOException {
        // Load client secrets.
        //     GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
        //             new InputStreamReader(DriveBackup.getInstance().getResource("googledrive_client_secrets.json")));

        // GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
        //           new InputStreamReader(DriveBackup.getInstance().getResource("googledrive_client_secrets.json")));
        if (DATA_STORE_FACTORY == null) {
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        }
        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(
                        httpTransport, JSON_FACTORY,
                        CLIENT_ID, CLIENT_SECRET, Collections.singletonList(DriveScopes.DRIVE))
                        .setDataStoreFactory(DATA_STORE_FACTORY)
                        .setAccessType("offline")
                        .build();
        Credential credential = new AuthorizationCodeInstalledApp(
                flow, new LocalServerReceiver()).authorize("user");

        // System.out.println(
        //         "Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }

    private static Drive getDriveService() throws IOException {
        Credential credential = authorize();
        return new Drive.Builder(
                httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }


    public static void uploadFile(java.io.File file, String type) throws IOException {
        Drive service = getDriveService();

        File body = new File();
        body.setTitle(file.getName());
        body.setDescription("DriveBackup plugin");
        body.setMimeType("application/zip");

        String destination = Config.getDestination();

        FileContent mediaContent = new FileContent("application/zip", file);

        File parentFolder = getFolder(destination);
        if (parentFolder == null) {
            System.out.println("Creating a folder");
            parentFolder = new File();
            parentFolder.setTitle(destination);
            parentFolder.setMimeType("application/vnd.google-apps.folder");
            parentFolder = service.files().insert(parentFolder).execute();
        }

        File childFolder = getFolder(type, parentFolder);
        ParentReference childFolderParent = new ParentReference();
        childFolderParent.setId(parentFolder.getId());
        if (childFolder == null) {
            System.out.println("Creating a folder");
            childFolder = new File();
            childFolder.setTitle(type);
            childFolder.setMimeType("application/vnd.google-apps.folder");
            childFolder.setParents(Collections.singletonList(childFolderParent));

            childFolder = service.files().insert(childFolder).execute();
        }

        ParentReference newParent = new ParentReference();
        newParent.setId(childFolder.getId());
        body.setParents(Collections.singletonList(newParent));

        try {
            service.files().insert(body, mediaContent).execute();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            if (Config.isDebug())
                e.printStackTrace();
        }

        deleteFiles(type);
    }

    private static File getFolder(String name, File parent) {
        try {
            Drive service = getDriveService();
            Drive.Files.List request = service.files().list().setQ(
                    "mimeType='application/vnd.google-apps.folder' and trashed=false and '" + parent.getId() + "' in parents");
            FileList files = request.execute();
            for (File folderfiles : files.getItems()) {
                if (folderfiles.getTitle().equals(name)) {

                    return folderfiles;
                }
            }
            return null;
        } catch (Exception e) {
            if (Config.isDebug())
                e.printStackTrace();
        }
        return null;
    }


    private static File getFolder(String name) {
        try {
            Drive service = getDriveService();
            Drive.Files.List request = service.files().list().setQ(
                    "mimeType='application/vnd.google-apps.folder' and trashed=false");
            FileList files = request.execute();
            for (File folderfiles : files.getItems()) {
                if (folderfiles.getTitle().equals(name)) {

                    return folderfiles;
                }
            }
            return null;
        } catch (Exception e) {
            if (Config.isDebug())
                e.printStackTrace();
        }
        return null;
    }

    private static List<ChildReference> processFiles(File folder) throws IOException {
        Drive service = getDriveService();

        //Create a List to store results
        List<ChildReference> result = new ArrayList<>();

        //Set up a request to query all files from all pages.
        //We are also making sure the files are sorted  by created Date. Oldest at the beginning of List.
        //Drive.Files.List request = service.files().list().setOrderBy("createdDate");
        //folder.getId();
        Drive.Children.List request = service.children().list(folder.getId()).setOrderBy("createdDate");
        //While there is a page available, request files and add them to the Result List.
        do {
            try {
                ChildList files = request.execute();
                result.addAll(files.getItems());
                request.setPageToken(files.getNextPageToken());
            } catch (IOException e) {
                if (Config.isDebug())
                    e.printStackTrace();
                request.setPageToken(null);
            }
        } while (request.getPageToken() != null &&
                request.getPageToken().length() > 0);

        return result;
    }

    public static void deleteFiles(String type) throws IOException {
        int fileLimit = Config.getKeepCount();

        if (fileLimit == -1) {
            return;
        }
        Drive service = getDriveService();
        //Set a limit for files

        File parentFolder = getFolder(Config.getDestination());

        File folder = getFolder(type, parentFolder);

        List<ChildReference> queriedFilesfromDrive = processFiles(folder);
        if (queriedFilesfromDrive.size() > fileLimit) {
            MessageUtil.sendConsoleMessage("There are " + queriedFilesfromDrive.size() + " file(s) which exceeds the " +
                    "limit of " + fileLimit + ", deleting.");

            for (Iterator<ChildReference> iterator = queriedFilesfromDrive.iterator(); iterator.hasNext(); ) {
                if (queriedFilesfromDrive.size() == fileLimit) {
                    break;
                }
                //System.out.println(queriedFilesfromDrive.size());
                ChildReference file = iterator.next();
                //System.out.println(file.get);
                Drive.Files.Delete removeItem = service.files().delete(file.getId());
                removeItem.execute();
                // System.out.println(file.getId());
                iterator.remove();
                // System.out.println(queriedFilesfromDrive.size());
            }
        }
    }
}

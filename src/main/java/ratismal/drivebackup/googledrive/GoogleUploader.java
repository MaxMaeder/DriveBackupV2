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
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.config.Config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class GoogleUploader {

    private static final String APPLICATION_NAME = "";

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
    private static boolean goodToGo = false;

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


    public static void uploadFile(java.io.File file, boolean useDirectUpload, String type) throws IOException {
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

        File parentFolder = getFolder(destination, service);
        if (parentFolder == null) {
            System.out.println("Creating a folder");
            parentFolder = new File();
            parentFolder.setTitle(destination);
            parentFolder.setMimeType("application/vnd.google-apps.folder");
            parentFolder = service.files().insert(parentFolder).execute();
        }

        File childFolder = getFolder(type, service);
        ParentReference childFolderParent = new ParentReference();
        childFolderParent.setId(parentFolder.getId());
        if (childFolder == null) {
            System.out.println("Creating a folder");
            childFolder = new File();
            childFolder.setTitle(type);
            childFolder.setMimeType("application/vnd.google-apps.folder");
            childFolder.setParents(Arrays.asList(childFolderParent));

            childFolder = service.files().insert(childFolder).execute();
        }

        ParentReference newParent = new ParentReference();
        newParent.setId(childFolder.getId());
        body.setParents(Arrays.asList(newParent));

        try {
            service.files().insert(body, mediaContent).execute();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static File getFolder(String name, Drive service) {
        try {
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
            e.printStackTrace();
        }
        return null;
    }

    public List<File> processFiles() throws IOException{
        Drive service = getDriveService();

        //Create a List to store results
        List<File> result = new ArrayList<>();

        //Set up a request to query all files from all pages.
        //We are also making sure the files are sorted  by created Date. Oldest at the beginning of List.
        Drive.Files.List request = service.files().list().setOrderBy("createdDate");

        //While there is a page available, request files and add them to the Result List.
        do{
            try {
                FileList files = request.execute();
                result.addAll(files.getItems());
                request.setPageToken(files.getNextPageToken());
            } catch(IOException e){
                e.printStackTrace();
                request.setPageToken(null);
            }
        }while (request.getPageToken() != null &&
                request.getPageToken().length() > 0);

        return result;
    }

    public void deleteFiles() throws IOException{
        Drive service = getDriveService();
        //Set a limit for files
        int fileLimit = 3;

        List<File> queriedFilesfromDrive = processFiles();
        if(queriedFilesfromDrive.size() > fileLimit){
            System.out.print("There are " + queriedFilesfromDrive.size() + " file(s) which exceeds the limit of " +  fileLimit + ", deleting.");

            for(Iterator<File> iterator = queriedFilesfromDrive.iterator(); iterator.hasNext();){
                if(queriedFilesfromDrive.size() == fileLimit){
                    break;
                }
                System.out.println(queriedFilesfromDrive.size());
                File file = iterator.next();
                System.out.println(file.getTitle());
                Drive.Files.Delete removeItem = service.files().delete(file.getId());
                removeItem.execute();
                System.out.println(file.getId());
                iterator.remove();
                System.out.println(queriedFilesfromDrive.size());
            }
        }
    }
}

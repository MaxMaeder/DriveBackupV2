package ratismal.drivebackup.onedrive;

import com.jayway.restassured.response.Response;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import ratismal.drivebackup.DriveBackup;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.util.MessageUtil;

import java.io.*;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static com.jayway.restassured.RestAssured.get;
import static com.jayway.restassured.RestAssured.given;

/**
 * Created by Redemption on 2/24/2016.
 */
public class OneDriveUploader {
    private String clientID;
    private String clientSecret;
    private String accessToken;
    private String refreshToken;
    private long totalUploaded;
    private long lastUploaded;
    private String userCode;

    private static final int CHUNK_SIZE = 5 * 1024 * 1024;
    private RandomAccessFile raf;
    private static final String CLIENT_JSON_PATH = DriveBackup.getInstance().getDataFolder().getAbsolutePath()
            + "/OneDriveCredential.json";

    private static final String CLIENT_ID = "000000004417D081";
    private static final String CLIENT_SECRET = "8e7pfZFCtJkaTiAEjq-FliLqfXhBSCQO";

    private static class Range {
        private final long start;
        private final long end;

        private Range(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    public OneDriveUploader() {
        try {
            processOneDriveConfig();
            setExistingTokens();
            retrieveTokens();
            retrieveNewAccessToken();
            setRanges(new String[0]);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void setRanges(String[] stringRanges) {
        Range[] ranges = new Range[stringRanges.length];
        for (int i = 0; i < stringRanges.length; i++) {
            long start = Long.parseLong(stringRanges[i].substring(0, stringRanges[i].indexOf('-')));

            String s = stringRanges[i].substring(stringRanges[i].indexOf('-') + 1);

            long end = 0;
            if (!s.isEmpty()) {
                end = Long.parseLong(s);
            }

            ranges[i] = new Range(start, end);
        }

        if (ranges.length > 0) {
            lastUploaded = ranges[0].start - totalUploaded;
            totalUploaded = ranges[0].start;
        }
    }

    private byte[] getChunk() throws IOException {

        byte[] bytes = new byte[CHUNK_SIZE];

        raf.seek(totalUploaded);
        int read = raf.read(bytes);

        if (read < CHUNK_SIZE) {
            bytes = Arrays.copyOf(bytes, read);
        }

        return bytes;
    }

    private void processOneDriveConfig() {
        setClientID();
        setClientSecret();
    }

    private void setExistingTokens() throws Exception {
        //Reading the OneDriveCredential.json and assigning variables if they exist for further processing.
        String clientJSON = processClientJsonFile();
        JSONParser jsonParser = new JSONParser();
        JSONObject clientJsonObject = (JSONObject) jsonParser.parse(clientJSON);
        this.userCode = (String) clientJsonObject.get("code");

        String authKey = (String) clientJsonObject.get("auth_key");
        String refreshKey = (String) clientJsonObject.get("refresh_key");


        if (authKey != null && !authKey.isEmpty()) {
            setAccessToken(authKey);
        } else
            setAccessToken("");

        if (refreshKey != null && !refreshKey.isEmpty()) {
            setRefreshToken(refreshKey);
        } else
            setRefreshToken("");
    }

    @SuppressWarnings("unchecked")
    private void retrieveTokens() throws Exception {
        if (returnAccessToken().isEmpty() && returnRefreshToken().isEmpty()) {
            String query = "https://login.live.com/oauth20_token.srf?client_id=" + returnClientID() + "&client_secret=" + returnClientSecret() + "&code=" + this.userCode + "&grant_type=authorization_code";

            Response response = given().contentType("application/x-www-form-urlencoded").get(query);
            setAccessToken(response.getBody().jsonPath().getString("access_token"));
            setRefreshToken(response.getBody().jsonPath().getString("refresh_token"));

            // Process the OneDriveCredential JSON file for future use
            String clientJSON = processClientJsonFile();
            JSONParser jsonParser = new JSONParser();
            JSONObject jsonObject = (JSONObject) jsonParser.parse(clientJSON);
            jsonObject.remove("auth_key");
            jsonObject.remove("refresh_key");
            jsonObject.put("auth_key", returnAccessToken());
            jsonObject.put("refresh_key", returnRefreshToken());

            // Write new keys to pre-existing file. Overwrites existing file
            FileWriter file = new FileWriter(CLIENT_JSON_PATH, false);
            file.write(jsonObject.toJSONString());
            file.close();
        }
    }

    private void retrieveNewAccessToken() {
        String query = "https://login.live.com/oauth20_token.srf?client_id=" + returnClientID() + "&client_secret=" + returnClientSecret() + "&refresh_token=" + returnRefreshToken() + "&grant_type=refresh_token";
        Response response = given().contentType("application/x-www-form-urlencoded").get(query);
        setAccessToken(response.getBody().jsonPath().getString("access_token"));
    }

    private boolean checkDestinationExists() {
        String query = "https://api.onedrive.com/v1.0/drive/root?expand=children&access_token=" + returnAccessToken();
        Response rootQuery = get(query);
        List<String> availableFolders = rootQuery.getBody().jsonPath().getList("children.name");
        return availableFolders.contains(Config.getDestination());
    }

    //Uses config specified Destination to createBackupDirectory
    private void createDestinationFolder() {
        System.out.println("Folder " + Config.getDestination() + " doesn't exist, creating");

        String query = "https://apis.live.net/v5.0/me/skydrive?access_token=" + returnAccessToken();
        given().contentType("application/json").body("{\"name\": \"" + Config.getDestination() + "\"}").post(query);
    }

    private boolean checkDestinationExists(String type) {
        String query = "https://api.onedrive.com/v1.0/drive/root:/" + Config.getDestination() + ":/children?access_token=" + returnAccessToken();
        Response rootQuery = get(query);

        try {
            List<String> availableFolders = rootQuery.getBody().jsonPath().getList("children.name");
            return availableFolders.contains(type);
        } catch (Exception e) {
            return false;
        }
    }

    //Uses config specified Destination to createBackupDirectory
    private void createDestinationFolder(String type) {
        String query1 = "https://api.onedrive.com/v1.0/drive/root:/" + Config.getDestination() + "?access_token=" + returnAccessToken();
        String id = get(query1).jsonPath().getString("id");

        String query2 = "https://api.onedrive.com/v1.0/drive/items/" + id + "/children" + "?access_token=" + returnAccessToken();
        given().contentType("application/json").body("{" +
                " \"name\": \"" + type + "\"," +
                " \"folder\": {}," +
                " \"@name.conflictBehavior\": \"fail\"" +
                "}").post(query2);
    }

    public void uploadFile(File file, String type) throws Exception {
        // URL Root = https://api.onedrive.com/v1.0
        // Two Accessible Models = Drive/Item

        //deleteFiles(type);

        if (!checkDestinationExists()) {
            createDestinationFolder();
        }

       if (!checkDestinationExists(type)) {
           createDestinationFolder(type);
       }

        String openQuery = "https://api.onedrive.com/v1.0/drive/root:/" + Config.getDestination() + "/" + type + "/" + file.getName() + ":/upload.createSession?access_token=" + returnAccessToken();
        Response openConnection = given().contentType("application/json").post(openQuery);

        //Assign our backup to Random Access File
        this.raf = new RandomAccessFile(file, "r");

        if (openConnection.statusCode() == 200) {
            String uploadURL = openConnection.getBody().jsonPath().get("uploadUrl");

            long fileSizeInBytes = file.length();
            long fileSizeInKB = fileSizeInBytes / 1024;
            long fileSizeInMB = fileSizeInKB / 1024;

            Response uploadFile;

            if (fileSizeInMB <= 100) {
                /* Implements 100mb limit since using Rest API */
                String uploadQuery = "https://api.onedrive.com/v1.0/drive/root:/" + Config.getDestination() + "/" + type + "/" + file.getName() + ":/content?access_token=" + returnAccessToken();
                given().contentType("application/zip").body(file).put(uploadQuery);
                MessageUtil.sendConsoleMessage("Uploaded " + fileSizeInMB + "MB of file " + file.getAbsolutePath());
            } else {
                boolean isComplete = false;

                while (!isComplete) {
                    long startTimeInner = System.currentTimeMillis();
                    byte[] bytesToUpload = getChunk();

                    if (getTotalUploaded() + bytesToUpload.length < file.length()) {
                        uploadFile = given().contentType("application/zip")
                                .header("Content-Range", String.format("bytes %d-%d/%d", getTotalUploaded(), getTotalUploaded() + bytesToUpload.length - 1, file.length()))
                                .body(bytesToUpload).put(uploadURL);
                        try {
                            List<String> test = uploadFile.getBody().jsonPath().getList("nextExpectedRanges");
                            setRanges(test.toArray(new String[test.size()]));
                        } catch (NullPointerException e) {
                            if (Config.isDebug())
                                e.printStackTrace();
                        }
                    } else {
                        given().contentType("application/zip")
                                .header("Content-Range", String.format("bytes %d-%d/%d", getTotalUploaded(), getTotalUploaded() + bytesToUpload.length - 1, file.length()))
                                .body(bytesToUpload).put(uploadURL);
                        isComplete = true;
                    }

                    long elapsedTimeInner = System.currentTimeMillis() - startTimeInner;
                    MessageUtil.sendConsoleMessage(String.format("Uploaded chunk (progress %.1f%%) of %s (%s/s) for file %s",
                            ((double) getTotalUploaded() / file.length()) * 100,
                            readableFileSize(getLastUploaded()),
                            elapsedTimeInner > 0 ? readableFileSize(getLastUploaded() / new Double(elapsedTimeInner / 1000d).longValue()) : 0,
                            file.getAbsoluteFile()));
                }
            }
        }

        if (!checkDestinationExists(type)) {
            deleteFiles(type);
        }
    }


    private void deleteFiles(String type) {
        int fileLimit = Config.getKeepCount();

        if (fileLimit == -1) {
            return;
        }

        String childFolderQuery = "https://api.onedrive.com/v1.0/drive/root:/" + Config.getDestination() + "/" + type + ":/children?sort_by=createdDateTime&access_token=" + returnAccessToken();
        Response childResponse = get(childFolderQuery);

        List<String> availableFileIDs = childResponse.getBody().jsonPath().getList("value.id");

        if(fileLimit < availableFileIDs.size()){
            MessageUtil.sendConsoleMessage("There are " + availableFileIDs.size() + " file(s) which exceeds the " +
                    "limit of " + fileLimit + ", deleting.");
        }

        for (Iterator<String> iterator = availableFileIDs.listIterator(); iterator.hasNext(); ) {
            String fileIDValue = iterator.next();
            if (fileLimit < availableFileIDs.size()) {
                String deleteQuery = "https://api.onedrive.com/v1.0/drive/items/" + fileIDValue + "?access_token=" + returnAccessToken();
                MessageUtil.sendConsoleMessage("Removing file with ID: " + fileIDValue);
                given().delete(deleteQuery);
                iterator.remove();
            }

            if(availableFileIDs.size() <= fileLimit){
                break;
            }
        }
    }

    private static String processClientJsonFile() {
        String result = "";
        try {
            BufferedReader br = new BufferedReader(new FileReader(CLIENT_JSON_PATH));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                line = br.readLine();
            }
            result = sb.toString();
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    private static String readableFileSize(long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private long getTotalUploaded() {
        return totalUploaded;
    }

    private long getLastUploaded() {
        return lastUploaded;
    }

    private void setAccessToken(String accessTokenValue) {
        this.accessToken = accessTokenValue;
    }

    private void setRefreshToken(String refreshTokenValue) {
        this.refreshToken = refreshTokenValue;
    }

    private void setClientID() {
        this.clientID = OneDriveUploader.CLIENT_ID;
    }

    private void setClientSecret() {
        this.clientSecret = OneDriveUploader.CLIENT_SECRET;
    }

    private String returnAccessToken() {
        return this.accessToken;
    }

    private String returnRefreshToken() {
        return this.refreshToken;
    }

    private String returnClientID() {
        return this.clientID;
    }

    private String returnClientSecret() {
        return this.clientSecret;
    }
}

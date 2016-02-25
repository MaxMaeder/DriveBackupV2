package ratismal.drivebackup.onedrive;

import com.jayway.restassured.response.Response;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import ratismal.drivebackup.config.Config;

import java.io.*;
import java.util.Arrays;
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

    private static final int CHUNK_SIZE = 5 * 1024 * 1024;
    private final String oneDriveConfig = getClass().getResource("/onedrive_client_secrets.json").toString();
    private RandomAccessFile raf;

    private static class Range {
        private long start;
        private long end;

        private Range(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    public OneDriveUploader(){
        try {
            processOneDriveConfig();
        } catch (Exception e){
            e.printStackTrace();
        }
        setExistingTokens();
        retrieveTokens("");
        retrieveNewAccessToken();
        setRanges(new String[0]);
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

    private void processOneDriveConfig() throws Exception{
        String jsonData = readFile(oneDriveConfig);
        JSONParser jsonParser = new JSONParser();
        JSONObject jsonObject = (JSONObject) jsonParser.parse(jsonData);
        JSONObject structure = (JSONObject) jsonObject.get("installed");
        setClientID(structure.get("client_id").toString());
        setClientSecret(structure.get("client_secret").toString());
    }

    private void setExistingTokens(){
        //We should read these tokens from the config if they exist.
        setAccessToken("");
        setRefreshToken("");
    }

    private void retrieveTokens(String code){
        if(returnAccessToken() == null && returnRefreshToken() == null) {
            String query = "https://login.live.com/oauth20_token.srf?client_id=" + returnClientID() + "&client_secret=" + returnClientSecret() + "&code=" + code + "&grant_type=authorization_code";

            Response response = given().contentType("application/x-www-form-urlencoded").get(query);
            response.prettyPrint();

            setAccessToken(response.getBody().jsonPath().getString("access_token"));
            setRefreshToken(response.getBody().jsonPath().getString("refresh_token"));
        }
    }

    private void retrieveNewAccessToken(){
        String query = "https://login.live.com/oauth20_token.srf?client_id=" + returnClientID() + "&client_secret=" + returnClientSecret() + "&refresh_token=" + returnRefreshToken() + "&grant_type=refresh_token";
        Response response = given().contentType("application/x-www-form-urlencoded").get(query);
        setAccessToken(response.getBody().jsonPath().getString("access_token"));
    }

    private boolean checkDestinationExists(){
        String query = "https://api.onedrive.com/v1.0/drive/root?expand=children&access_token=" + returnAccessToken();
        Response rootQuery = get(query);
        List<String> availableFolders = rootQuery.getBody().jsonPath().getList("children.name");
       return availableFolders.contains("backups");
    }

    //Uses config specified Destination to createBackupDirectory
    private void createDestinationFolder(){
        String query = "https://apis.live.net/v5.0/me/skydrive?access_token=" + returnAccessToken();
        given().contentType("application/json").body("{\"name\": \"" + Config.getDestination() + "\"}").post(query);
    }

    public void uploadFile(File file) throws Exception{
        // URL Root = https://api.onedrive.com/v1.0
        // Two Accessible Models = Drive/Item

        if(!checkDestinationExists()) {
            createDestinationFolder();
        }

        String openQuery = "https://api.onedrive.com/v1.0/drive/root:/" + Config.getDestination() + "/" + file.getName() + ":/upload.createSession?access_token=" + returnAccessToken();
        Response openConnection = given().contentType("application/json").post(openQuery);

        //Assign our backup to Random Access File
        this.raf = new RandomAccessFile(file, "r");

        if(openConnection.statusCode() == 200) {
            String uploadURL = openConnection.getBody().jsonPath().get("uploadUrl");

            long fileSizeInBytes = file.length();
            long fileSizeInKB = fileSizeInBytes / 1024;
            long fileSizeInMB = fileSizeInKB / 1024;

            Response uploadFile;

            if(fileSizeInMB > 100){
                /* Implements 100mb limit since using Rest API */
                String uploadQuery = "https://api.onedrive.com/v1.0/drive/root:/" + Config.getDestination() + "/" + file.getName() + ":/content?access_token=" + returnAccessToken();
                given().contentType("application/zip").body(file).put(uploadQuery);
            } else {
                boolean isComplete = false;

                while (!isComplete) {
                    byte[] bytesToUpload = getChunk();

                    if (getTotalUploaded() + bytesToUpload.length < file.length()) {
                        uploadFile = given().contentType("application/zip")
                                .header("Content-Range", String.format("bytes %d-%d/%d", getTotalUploaded(), getTotalUploaded() + bytesToUpload.length - 1, file.length()))
                                .body(bytesToUpload).put(uploadURL);

                        try {
                            List<String> test = uploadFile.getBody().jsonPath().getList("nextExpectedRanges");
                            setRanges(test.toArray(new String[test.size()]));
                        } catch (NullPointerException e) {
                            e.printStackTrace();
                        }
                    } else {
                        given().contentType("application/zip")
                                .header("Content-Range", String.format("bytes %d-%d/%d", getTotalUploaded(), getTotalUploaded() + bytesToUpload.length - 1, file.length()))
                                .body(bytesToUpload).put(uploadURL);
                        isComplete = true;
                    }
                }
            }
        }
    }

    private static String readFile(String filename) {
        String result = "";
        try {
            BufferedReader br = new BufferedReader(new FileReader(filename));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            while (line != null) {
                sb.append(line);
                line = br.readLine();
            }
            result = sb.toString();
            br.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
        return result;
    }


    private long getTotalUploaded() {
        return totalUploaded;
    }

    private void setAccessToken(String accessTokenValue){
        this.accessToken = accessTokenValue;
    }

    private void setRefreshToken(String refreshTokenValue){
        this.refreshToken = refreshTokenValue;
    }

    private void setClientID(String clientIdValue){
        this.clientID = clientIdValue;
    }

    private void setClientSecret(String clientSecretValue){
        this.clientSecret = clientSecretValue;
    }

    private String returnAccessToken(){
        return this.accessToken;
    }

    private String returnRefreshToken(){
        return this.refreshToken;
    }

    private String returnClientID(){
        return this.clientID;
    }

    private String returnClientSecret(){
        return this.clientSecret;
    }
}

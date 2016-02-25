package ratismal.drivebackup.onedrive;

import com.jayway.restassured.response.Response;

import java.io.File;

/**
 * Created by Ratismal on 2016-02-23.
 */

import static com.jayway.restassured.RestAssured.*;

public class OneDriveUploader {

    private String clientID;
    private String clientSecret;
    private String accessToken;
    private String refreshToken;
    private String userID;

    // 000000004417D081
    // 8e7pfZFCtJkaTiAEjq-FliLqfXhBSCQO

    // https://login.live.com/oauth20_authorize.srf?client_id=000000004417D081&scope=wl.signin%20wl.basic%20wl.offline_access%20wl.skydrive_update&response_type=code&redirect_uri=https://login.live.com/oauth20_desktop.srf
    // https://login.live.com/oauth20_authorize.srf?client_id=000000004417D081&scope=wl.signin%20wl.basic%20wl.offline_access%20wl.skydrive_update&response_type=code

    // http://0c131d5b-87f3-48c9-80e3-0211dc6dadd0.apps.dev.live.com/?code=Mb10f408e-f432-f096-febd-f130325b3412

    // https://login.live.com/oauth20_token.srf?client_id=000000004417D081&client_secret=8e7pfZFCtJkaTiAEjq-FliLqfXhBSCQO&code=Mb10f408e-f432-f096-febd-f130325b3412&grant_type=authorization_code

    // EwCAAq1DBAAUGCCXc8wU/zFu9QnLdZXy+YnElFkAASQzxYv+xTICvAl18Z4aPF2QSwsvjsw224RiY1yYp4i+4pmvTEs4Rwzkyz3nLEvhoVCjf6JI+GfHvluQCOAdCsPjQp6W4/wbl5KJyUvEtMG34gjbZti8SFX+CJ0SHppcmMUdKs+DNL0o3Xmg8An3hmqfXwlUOi95shtkuCH3hwKhJvjQtOAX8d6/E5DlKxzCiDJ7hMheEj5DIyc1jcVrQHGsC3/dqGckCPvXIgRcVwAq8Vvg85yJMM/TUCBUF61vsv8Fc1PuFUAtlWlfdx3KhOCMPygmHm/0TWrY7OfbwFM/iKzzXRQmrF05xGU4UZBxFbjSDlDEWijbiSBLFMk8bEkDZgAACGkQGoEpuHSGUAFFILpv+8gMUUyDEVEpvWUX3Ac2MqDzswPmVNRWTBbHdKGVB/i9ZQZI5fLwF0nPse70DcDJsDpGkN1Woa1KIOtk3mlDzZxl2yBFkC7wBBnRov/22nwC6/Q/LLow8oeMkTuY6LClu/KDq06ob5pEYsxWPE6v6eDxali2g99lNT3W90BAmhJEYi2HmbmmZimzgjAgdWGXp0PDJywlrFuQv4m6SiUOnV3xhyQ16G4gO8oxAh7iXBE9bpxciAjd6lx6OnbVIyg+rU3LnS/cQZaaAPcKcNXhn4OUFNJk3i2ns8EZaoy2MmDtgg++c3KErK2o32Ex9NHe6vYSGk3Ex+UdUnlO9otJ18IEI5UqEuzvFD+Prw8EpNv8ZDGDZJDwk+Lc9v2rEsDIwjtUBBCp7gOe71xCkFdDvUF7Lo6AtWnhK59XYEEOco4uQ58n+eEtp8/lOWRlAQ==
    // MCZVOtD0GINz5kJAC4vFAAzDA6ei!tFhQlgmb7Gf7be6r0zKauTYs1r0Z0F7YAsA6yic1Wxcd15E4v8wuP83XMZ5!kemL7N3L*jxxkhvBo6GKdM431kvnS7jv91hJ5RWJYo3Mj8MtaLpkoj7mKOmvwywWYEIAW4fwAMV0w8D0bnPObt7WjMJX9QM4wW5GXHLsdlnC7DqofmJcYQ27vyULoSTtcoC9ibBBjm9BCxx*PE4gMB0AIxtoZZzkDgLROoX06imFOxRJRbg98R*ULvlaM3sk9WuhSV4lhYM9y9ctviyb6yJ8YX7lGNGrToE0TK8izSGS7BnX2j9z3wdtkhYYSnYuOcklJzabWVB3sdPF53PODmYG2Ge2F3cWEVSZ*QhyQeMDH1MJgKgH6266ZwYoozsFpHO!uotBa36FiNYXrPy2


    private void setExistingTokens() {
        //We should read these tokens from the config if they exist.
        setAccessToken("EwCAAq1DBAAUGCCXc8wU/zFu9QnLdZXy+YnElFkAASQzxYv+xTICvAl18Z4aPF2QSwsvjsw224RiY1yYp4i+4pmvTEs4Rwzkyz3nLEvhoVCjf6JI+GfHvluQCOAdCsPjQp6W4/wbl5KJyUvEtMG34gjbZti8SFX+CJ0SHppcmMUdKs+DNL0o3Xmg8An3hmqfXwlUOi95shtkuCH3hwKhJvjQtOAX8d6/E5DlKxzCiDJ7hMheEj5DIyc1jcVrQHGsC3/dqGckCPvXIgRcVwAq8Vvg85yJMM/TUCBUF61vsv8Fc1PuFUAtlWlfdx3KhOCMPygmHm/0TWrY7OfbwFM/iKzzXRQmrF05xGU4UZBxFbjSDlDEWijbiSBLFMk8bEkDZgAACGkQGoEpuHSGUAFFILpv+8gMUUyDEVEpvWUX3Ac2MqDzswPmVNRWTBbHdKGVB/i9ZQZI5fLwF0nPse70DcDJsDpGkN1Woa1KIOtk3mlDzZxl2yBFkC7wBBnRov/22nwC6/Q/LLow8oeMkTuY6LClu/KDq06ob5pEYsxWPE6v6eDxali2g99lNT3W90BAmhJEYi2HmbmmZimzgjAgdWGXp0PDJywlrFuQv4m6SiUOnV3xhyQ16G4gO8oxAh7iXBE9bpxciAjd6lx6OnbVIyg+rU3LnS/cQZaaAPcKcNXhn4OUFNJk3i2ns8EZaoy2MmDtgg++c3KErK2o32Ex9NHe6vYSGk3Ex+UdUnlO9otJ18IEI5UqEuzvFD+Prw8EpNv8ZDGDZJDwk+Lc9v2rEsDIwjtUBBCp7gOe71xCkFdDvUF7Lo6AtWnhK59XYEEOco4uQ58n+eEtp8/lOWRlAQ==");
        setRefreshToken("MCZVOtD0GINz5kJAC4vFAAzDA6ei!tFhQlgmb7Gf7be6r0zKauTYs1r0Z0F7YAsA6yic1Wxcd15E4v8wuP83XMZ5!kemL7N3L*jxxkhvBo6GKdM431kvnS7jv91hJ5RWJYo3Mj8MtaLpkoj7mKOmvwywWYEIAW4fwAMV0w8D0bnPObt7WjMJX9QM4wW5GXHLsdlnC7DqofmJcYQ27vyULoSTtcoC9ibBBjm9BCxx*PE4gMB0AIxtoZZzkDgLROoX06imFOxRJRbg98R*ULvlaM3sk9WuhSV4lhYM9y9ctviyb6yJ8YX7lGNGrToE0TK8izSGS7BnX2j9z3wdtkhYYSnYuOcklJzabWVB3sdPF53PODmYG2Ge2F3cWEVSZ*QhyQeMDH1MJgKgH6266ZwYoozsFpHO!uotBa36FiNYXrPy2");
    }

    private void retrieveTokens(String clientID, String clientSecret, String code) {
        //RestAssured.urlEncodingEnabled = true;
        //RestAssured.baseURI = "https://login.live.com/oauth20_token.srf?client_id=" + clientID + "&client_secret=" + clientSecret + "&code=" + code + "grant_type=authorization_code";
        //RestAssured.port = 8080;
        //RestAssured.basePath = "";


        setClientID(clientID);
        setClientSecret(clientSecret);

        if (returnAccessToken() == null && returnRefreshToken() == null) {
            String query = "https://login.live.com/oauth20_token.srf?client_id=" + returnClientID() + "&client_secret=" + returnClientSecret() + "&code=" + code + "&grant_type=authorization_code";

            Response response = given().contentType("application/x-www-form-urlencoded").get(query);
            response.prettyPrint();

            setAccessToken(response.getBody().jsonPath().getString("access_token"));
            setRefreshToken(response.getBody().jsonPath().getString("refresh_token"));
            setUserID(response.getBody().jsonPath().getString("user_id"));
        }

        System.out.println(returnAccessToken());
        System.out.println(returnRefreshToken());
    }

    private void retrieveNewAccessToken() {
        //https://login.live.com/oauth20_token.srf?client_id=0000000123ABCD&refresh_token=a_very_long_refresh_token_of_hunderds_of_characters&grant_type=refresh_token&redirect_uri=https://login.live.com/oauth20_desktop.srf
        String query = "https://login.live.com/oauth20_token.srf?client_id=" + returnClientID() + "&client_secret=" + returnClientSecret() + "&refresh_token=" + returnRefreshToken() + "&grant_type=refresh_token";
        Response response = given().contentType("application/x-www-form-urlencoded").get(query);
        response.prettyPrint();
        setAccessToken(response.getBody().jsonPath().getString("access_token"));
        setUserID(response.getBody().jsonPath().getString("user_id"));
        System.out.println(returnAccessToken());
    }

    private void createBackupFolder() {
        //Maybe Specify Directory name? Replace backups with config specified name?
        String dirName = "backups";
        String query = "https://apis.live.net/v5.0/me/skydrive?access_token=" + returnAccessToken();
        Response response = given().contentType("application/json").body("{\"name\": \"" + dirName + "\"}").post(query);
        response.prettyPrint();
    }

    private void uploadBackup(File file) throws Exception {
        //URL Root = https://api.onedrive.com/v1.0

        // Two Accessible Models = Drive/Item

//        100 Mb limit...
        String openQuery = "https://api.onedrive.com/v1.0/drive/root:/testname:/upload.createSession?access_token=" + returnAccessToken();
        Response openConnection = given().contentType("application/json").post(openQuery);
        openConnection.prettyPrint();

        if (openConnection.statusCode() == 200) {
            String uploadURL = openConnection.getBody().jsonPath().get("uploadUrl");
            String expirationDateTime = openConnection.getBody().jsonPath().get("expirationDateTime");
        }

        //To-do
        //Add code to upload file to URL specified above.
    }

    private void setAccessToken(String accessTokenValue) {
        this.accessToken = accessTokenValue;
    }

    private void setRefreshToken(String refreshTokenValue) {
        this.refreshToken = refreshTokenValue;
    }

    private void setClientID(String clientIdValue) {
        this.clientID = clientIdValue;
    }

    private void setClientSecret(String clientSecretValue) {
        this.clientSecret = clientSecretValue;
    }

    private void setUserID(String userIDValue) {
        this.userID = userIDValue;
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

    private String returnUserID() {
        return this.userID;
    }

    public static void main(String[] args) throws Exception {
        OneDriveUploader dt = new OneDriveUploader();
        dt.setExistingTokens();
        dt.retrieveTokens("000000004417D081", "8e7pfZFCtJkaTiAEjq-FliLqfXhBSCQO", "Mc9e0125d-6787-6b42-f964-55cd3e1699a0");
        dt.retrieveNewAccessToken();
        dt.createBackupFolder();
        //dt.uploadBackup();
    }
}

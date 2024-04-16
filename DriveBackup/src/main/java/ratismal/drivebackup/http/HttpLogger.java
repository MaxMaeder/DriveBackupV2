package ratismal.drivebackup.http;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import ratismal.drivebackup.platforms.DriveBackupInstance;
import ratismal.drivebackup.uploaders.UploaderUtils;

import java.io.IOException;

public final class HttpLogger implements Interceptor {
    
    private static DriveBackupInstance instance;
    
    public static void setInstance(DriveBackupInstance instance) {
        HttpLogger.instance = instance;
    }
    
    @Override
    public @NotNull Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
        Request request = chain.request();
        if (instance == null || !instance.getConfigHandler().getConfig().getValue("advanced" , "debug").getBoolean()) {
            return chain.proceed(request);
        }
        long t1 = System.nanoTime();
        instance.getLoggingHandler().info(String.format("Sending request %s", request.url()));
        Response response = chain.proceed(request);
        long t2 = System.nanoTime();
        instance.getLoggingHandler().info(String.format("Received response for %s in %.1fms", response.request().url(), (t2 - t1) / 1.0e6d));
        try {
            if (request.body().contentType().equals(UploaderUtils.getJsonMediaType())) {
                Buffer requestBody = new Buffer();
                request.body().writeTo(requestBody);
                instance.getLoggingHandler().info("Req: " + requestBody.readUtf8());
            } else {
                instance.getLoggingHandler().info("Req: Not JSON");
            }
        } catch (Exception exception) {
            instance.getLoggingHandler().info("Req: None");
        }
        ResponseBody responseBody = response.body();
        String responseBodyString = responseBody.string();
        MediaType responseBodyContentType = responseBody.contentType();
        responseBody.close();
        if (responseBodyContentType.equals(UploaderUtils.getJsonMediaType())) {
            try {
                JSONObject responseBodyJson = new JSONObject(responseBodyString);
                if (responseBodyJson.getString("msg").equals("code_not_authenticated")) {
                    return response.newBuilder().body(ResponseBody.create(responseBodyString, responseBodyContentType)).build();
                }
                instance.getLoggingHandler().info("Resp: " + responseBodyJson);
            } catch (Exception exception) {
                instance.getLoggingHandler().info("Resp: " + responseBodyString);
            }
        } else {
            instance.getLoggingHandler().info("Resp: " + responseBodyString);
        }
        return response.newBuilder().body(ResponseBody.create(responseBodyString, responseBodyContentType)).build();
    }
}

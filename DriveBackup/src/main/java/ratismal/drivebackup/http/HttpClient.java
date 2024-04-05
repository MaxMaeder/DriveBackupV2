package ratismal.drivebackup.http;

import okhttp3.OkHttpClient;
import org.jetbrains.annotations.Contract;
import ratismal.drivebackup.util.HttpLogger;

import java.util.concurrent.TimeUnit;

public final class HttpClient {
    
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(1L, TimeUnit.MINUTES)
            .writeTimeout(3L, TimeUnit.MINUTES)
            .readTimeout(3L, TimeUnit.MINUTES)
            .addInterceptor(new HttpLogger())
            .build();
    
    @Contract (pure = true)
    private HttpClient() {
    }
    
    @Contract (pure = true)
    public static OkHttpClient getHttpClient() {
        return httpClient;
    }
    
}

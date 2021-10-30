package ratismal.drivebackup.util;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import ratismal.drivebackup.config.ConfigParser;

public class HttpLogger implements Interceptor {
  private static final MediaType jsonMediaType = MediaType.parse("application/json; charset=utf-8");

  @Override
  public Response intercept(Interceptor.Chain chain) throws IOException {
    Request request = chain.request();
    if (!ConfigParser.getConfig().advanced.debugEnabled) return chain.proceed(request);

    long t1 = System.nanoTime();
    MessageUtil.Builder().text(String.format("Sending request %s", request.url())).toConsole(true).send();

    Response response = chain.proceed(request);

    long t2 = System.nanoTime();
    MessageUtil.Builder().text(String.format("Received response for %s in %.1fms", response.request().url(), (t2 - t1) / 1e6d)).toConsole(true).send();

    try {
      if (request.body().contentType().equals(jsonMediaType)) {
        final Buffer requestBody = new Buffer();
        request.body().writeTo(requestBody);
        MessageUtil.Builder().text("Req: " + requestBody.readUtf8()).toConsole(true).send();
      } else {
        MessageUtil.Builder().text("Req: Not JSON").toConsole(true).send();
      }
    } catch (Exception exception) {
      MessageUtil.Builder().text("Req: None").toConsole(true).send();
    }

    ResponseBody responseBody = response.body();
    String responseBodyString = responseBody.string();
    MediaType responseBodyContentType = responseBody.contentType();
    responseBody.close();
    MessageUtil.Builder().text("Res: " + responseBodyString).toConsole(true).send();

    return response.newBuilder().body(ResponseBody.create(responseBodyString, responseBodyContentType)).build();
  }
}
package ratismal.drivebackup.util;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

public class HttpLogger implements Interceptor {
  private static final MediaType jsonMediaType = MediaType.parse("application/json; charset=utf-8");

  @Override
  public Response intercept(Interceptor.Chain chain) throws IOException {
    Request request = chain.request();

    long t1 = System.nanoTime();
    MessageUtil.sendConsoleMessage(
        String.format("Sending request %s on %s%n%s", request.url(), chain.connection(), request.headers()));

    Response response = chain.proceed(request);

    long t2 = System.nanoTime();
    MessageUtil.sendConsoleMessage(String.format("Received response for %s in %.1fms%n%s", response.request().url(), (t2 - t1) / 1e6d, response.headers()));

    try {
      if (request.body().contentType().equals(jsonMediaType)) {
        final Buffer requestBody = new Buffer();
        request.body().writeTo(requestBody);
        MessageUtil.sendConsoleMessage("req: " + requestBody.readUtf8());
      } else {
        MessageUtil.sendConsoleMessage("req: not JSON");
      }
    } catch (Exception exception) {
      MessageUtil.sendConsoleMessage("req: none");
    }

    ResponseBody responseBody = response.body();
    String responseBodyString = responseBody.string();
    MediaType responseBodyContentType = responseBody.contentType();
    responseBody.close();
    MessageUtil.sendConsoleMessage("res: " + responseBodyString);

    return response.newBuilder().body(ResponseBody.create(responseBodyString, responseBodyContentType)).build();
  }
}
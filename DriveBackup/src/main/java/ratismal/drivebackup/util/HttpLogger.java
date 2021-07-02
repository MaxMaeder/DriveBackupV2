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
    new MessageUtil(String.format("Sending request %s on %s%n%s", request.url(), chain.connection(), request.headers())).toConsole(true).send();

    Response response = chain.proceed(request);

    long t2 = System.nanoTime();
    new MessageUtil(String.format("Received response for %s in %.1fms%n%s", response.request().url(), (t2 - t1) / 1e6d, response.headers())).toConsole(true).send();

    try {
      if (request.body().contentType().equals(jsonMediaType)) {
        final Buffer requestBody = new Buffer();
        request.body().writeTo(requestBody);
        new MessageUtil("req: " + requestBody.readUtf8()).toConsole(true).send();
      } else {
        new MessageUtil("req: not JSON").toConsole(true).send();
      }
    } catch (Exception exception) {
      new MessageUtil("req: none").toConsole(true).send();
    }

    ResponseBody responseBody = response.body();
    String responseBodyString = responseBody.string();
    MediaType responseBodyContentType = responseBody.contentType();
    responseBody.close();
    new MessageUtil("res: " + responseBodyString).toConsole(true).send();

    return response.newBuilder().body(ResponseBody.create(responseBodyString, responseBodyContentType)).build();
  }
}
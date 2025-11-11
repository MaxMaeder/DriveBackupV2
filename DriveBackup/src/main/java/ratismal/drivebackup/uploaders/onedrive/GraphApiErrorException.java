package ratismal.drivebackup.uploaders.onedrive;

import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;

/**
 * an exception representing a microsoft graph api error
 */
public class GraphApiErrorException extends Exception {
    private static final String ERROR_OBJ_KEY = "error";
    private static final String CODE_STR_KEY = "code";
    private static final String MESSAGE_STR_KEY = "message";

    /** status code of the response */
    public final int statusCode;
    /** an error code string for the error that occurred */
    public final String errorCode;
    /** a developer ready message about the error that occurred. this shouldn't be displayed to the user directly */
    public final String errorMessage;
    /** the full error object */
    public final JSONObject errorObject;

    /**
     * create the exception from a response
     *
     * @param response to parse error from its body
     * @throws IOException          if the body string could not be loaded
     * @throws NullPointerException if the body could not be loaded
     * @throws JSONException        if the body does not contain the expected json values
     */
    public GraphApiErrorException(@NotNull Response response) throws IOException {
        this(response.code(), new JSONObject(response.body().string()).getJSONObject(ERROR_OBJ_KEY));
    }

    /**
     * create the exception from a status code and response body
     *
     * @param statusCode of the response
     * @param responseBody of the response
     * @throws JSONException if the body does not contain the expected json values
     */
    public GraphApiErrorException(int statusCode, @NotNull String responseBody) {
        this(statusCode, new JSONObject(responseBody).getJSONObject(ERROR_OBJ_KEY));
    }

    private GraphApiErrorException(int statusCode, @NotNull JSONObject error) {
        this(statusCode, error.getString(CODE_STR_KEY), error.getString(MESSAGE_STR_KEY), error);
    }

    private static String toMessage(int statusCode, String errorCode, String errorMessage, JSONObject errorObject) {
        String format = "%d %s : \"%s\"\n%s";
        return String.format(format, statusCode, errorCode, errorMessage, errorObject.toString(2));
    }

    private GraphApiErrorException(int statusCode, String errorCode, String errorMessage, JSONObject errorObject) {
        super(toMessage(statusCode, errorCode, errorMessage, errorObject));
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.errorObject = errorObject;
    }
}

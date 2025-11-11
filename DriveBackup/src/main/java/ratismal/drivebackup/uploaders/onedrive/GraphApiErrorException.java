package ratismal.drivebackup.uploaders.onedrive;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.json.JSONException;

import static ratismal.drivebackup.util.JsonUtil.optJsonObjectIgnoreCase;
import static ratismal.drivebackup.util.JsonUtil.optStringIgnoreCase;

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
    public final @NotNull String errorCode;
    /** a developer ready message about the error that occurred. this shouldn't be displayed to the user directly */
    public final @NotNull String errorMessage;
    /** the full error object */
    public final @Nullable JSONObject errorObject;

    /**
     * create the exception from a status code and response body
     *
     * @param statusCode   of the response
     * @param jsonResponse string of the response body
     * @throws JSONException if the body does not contain the expected json values
     */
    public GraphApiErrorException(int statusCode, @NotNull String jsonResponse) {
        this(statusCode, new ParsedError(jsonResponse));
    }

    /** parsing logic that needs to happen before calling this/super constructor */
    private static class ParsedError {
        public final @NotNull String errorCode;
        public final @NotNull String errorMessage;
        public final @Nullable JSONObject errorObject;

        public ParsedError(@NotNull String responseBody) {
            JSONObject errorResponse;
            try {
                errorResponse = new JSONObject(responseBody);
            } catch (JSONException jsonException) {
                this.errorCode = "invalidErrorResponse";
                this.errorMessage = String.valueOf(jsonException.getMessage());
                this.errorObject = null;
                return;
            }
            JSONObject errorObject = optJsonObjectIgnoreCase(errorResponse, ERROR_OBJ_KEY);
            if (errorObject == null) {
                this.errorCode = "invalidErrorResponse";
                this.errorMessage = "error response does not contain an json object 'error'";
                this.errorObject = null;
                return;
            }

            this.errorCode = optStringIgnoreCase(errorObject, CODE_STR_KEY, "invalid/missing member 'code'");
            this.errorMessage = optStringIgnoreCase(errorObject, MESSAGE_STR_KEY, "invalid/missing member 'message'");
            this.errorObject = errorObject;
        }
    }

    private static String toMessage(int statusCode, @NotNull ParsedError error) {
        String format = "%d %s : \"%s\"";
        if (error.errorObject == null) {
            return String.format(format, statusCode, error.errorCode, error.errorMessage);
        }
        return String.format(format + "\n%s", statusCode, error.errorCode, error.errorMessage,
            error.errorObject.toString(2));
    }

    private GraphApiErrorException(int statusCode, @NotNull ParsedError error) {
        super(toMessage(statusCode, error));
        this.statusCode = statusCode;
        this.errorCode = error.errorCode;
        this.errorMessage = error.errorMessage;
        this.errorObject = error.errorObject;
    }
}

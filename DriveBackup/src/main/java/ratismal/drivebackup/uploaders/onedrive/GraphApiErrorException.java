package ratismal.drivebackup.uploaders.onedrive;

import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * an exception representing a microsoft graph api error
 */
public class GraphApiErrorException extends Exception {
    private static final String ERROR_OBJ_KEY = "error";
    private static final String CODE_STR_KEY = "code";
    private static final String INNERERROR_OBJ_KEY = "innererror";
    private static final String MESSAGE_STR_KEY = "message";
    private static final String DETAILS_ARR_KEY = "details";

    /** status code of the response or -1 if not available */
    public final int statusCode;
    /** an error code string for the error that occurred */
    public final String errorCode;
    /** a developer ready message about the error that occurred. this shouldn't be displayed to the user directly */
    public final String errorMessage;
    /** optional list of additional error objects that might be more specific than the top-level error */
    public final List<String> innerErrors;
    /**
     * optional list of additional error objects that might provide a breakdown of multiple errors encountered
     * while processing the request
     */
    public final List<GraphApiErrorException> details;

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

    private static List<String> parseInnerErrors(@Nullable JSONObject innerErrors) {
        List<String> list = new ArrayList<>();
        while (innerErrors != null) {
            String errorCode = innerErrors.optString(CODE_STR_KEY);
            if (errorCode != null) {
                list.add(errorCode);
            }
            innerErrors = innerErrors.optJSONObject(INNERERROR_OBJ_KEY);
        }
        return list;
    }

    private static List<GraphApiErrorException> parseDetails(@Nullable JSONArray details) {
        if (details == null) {
            return new ArrayList<>();
        }
        List<GraphApiErrorException> list = new ArrayList<>(details.length());
        for (int detailIdx = 0; detailIdx < details.length(); detailIdx++) {
            list.add(new GraphApiErrorException(-1, details.getJSONObject(detailIdx).getJSONObject(ERROR_OBJ_KEY)));
        }
        return list;
    }

    private GraphApiErrorException(int statusCode, @NotNull JSONObject error) {
        this(statusCode, error.getString(CODE_STR_KEY), error.getString(MESSAGE_STR_KEY),
            parseInnerErrors(error.optJSONObject(INNERERROR_OBJ_KEY)),
            parseDetails(error.optJSONArray(DETAILS_ARR_KEY)));
    }

    private static String toMessage(int statusCode, String errorCode, String errorMessage, List<String> innerErrors,
        List<GraphApiErrorException> details) {
        String format = "%d %s : \"%s\"%s%s";
        String inner = String.join("\", \"", innerErrors);
        String detail = details.stream().map(GraphApiErrorException::getMessage).collect(Collectors.joining(" }, { "));
        if (!inner.isEmpty()) {
            inner = " inner:[ \"" + inner + "\" ]";
        }
        if (!detail.isEmpty()) {
            detail = " details:[ { " + detail + " } ]";
        }
        return String.format(format, statusCode, errorCode, errorMessage, inner, detail);
    }

    private GraphApiErrorException(int statusCode, String errorCode, String errorMessage, List<String> innerErrors,
        List<GraphApiErrorException> details) {
        super(toMessage(statusCode, errorCode, errorMessage, innerErrors, details));
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.innerErrors = innerErrors;
        this.details = details;
    }
    
}

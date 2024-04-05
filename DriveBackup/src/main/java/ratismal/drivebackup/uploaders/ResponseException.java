package ratismal.drivebackup.uploaders;

import org.json.JSONObject;

import java.io.IOException;

/**
 * Thrown to indicate an error with the response of an http request.
 */
public class ResponseException extends IOException {
    private final JSONObject json;

    /**
     * Constructs an {@code ResponseException} with the
     * specified JSON response.
     *
     * @param j the JSON response.
     */
    public ResponseException(JSONObject j) {
        json = j;
    }

    /**
     * Constructs an {@code ResponseException} with the
     * specified JSON response and detail message.
     *
     * @param j the JSON response.
     * @param message the detail message.
     */
    public ResponseException(JSONObject j, String message) {
        super(message);
        json = j;
    }

    /**
     * Gets the JSON response
     * @return the JSON response
     */
    public JSONObject getJsonObject() {
        return json;
    }
}

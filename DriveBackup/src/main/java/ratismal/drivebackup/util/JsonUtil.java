package ratismal.drivebackup.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.Iterator;

public class JsonUtil {
    /**
     * tries to get the {@link JSONObject} associated with a key, ignoring case differences
     *
     * @param json object to lookup in
     * @param key  string to compare against
     * @return {@link JSONObject} or null if the value was not found or is not a {@link JSONObject}
     */
    @Nullable
    public static JSONObject optJsonObjectIgnoreCase(@NotNull JSONObject json, @NotNull String key) {
        return json.optJSONObject(findKeyIgnoreCase(json, key));
    }

    /**
     * tries to get the {@link String} associated with a key, ignoring case differences. if the value is not a string
     * and is not null, then it is converted to a string
     *
     * @param json object to lookup in
     * @param key  string to compare against
     * @param alt  string to return if not found
     * @return {@link String} or alt if the value was not found
     */
    @NotNull
    public static String optStringIgnoreCase(@NotNull JSONObject json, @NotNull String key, @NotNull String alt) {
        return json.optString(findKeyIgnoreCase(json, key), alt);
    }

    /**
     * tries to find a key in json that matches the given key, ignoring case differences. an exact match is preferred.
     * otherwise any case-insensitive match is returned, or null if nothing is found.
     *
     * @param json object to search in
     * @param key  string to search for
     * @return the matching key if found, or null
     */
    @Nullable
    public static String findKeyIgnoreCase(@NotNull JSONObject json, @NotNull String key) {
        if (json.has(key)) {
            return key;
        }
        Iterator<String> keyIt = json.keys();
        while (keyIt.hasNext()) {
            String member = keyIt.next();
            if (member.equalsIgnoreCase(key)) {
                return member;
            }
        }
        return null;
    }
}

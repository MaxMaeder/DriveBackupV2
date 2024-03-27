package ratismal.drivebackup.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public final class Version {
    
    private static final Pattern VERSION_STRING = Pattern.compile("^\\d+\\.\\d+\\.\\d+");
    private final int major;
    private final int minor;
    private final int patch;
    
    @Contract (pure = true)
    private Version(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }
    
    @NotNull
    @Contract ("_ -> new")
    public static Version parse(String version) throws NumberFormatException {
        if (version == null) {
            throw new IllegalArgumentException("Version string cannot be null");
        }
        String trimmed = version.trim();
        if (trimmed.contains("-")) {
            trimmed = trimmed.split("-")[0];
        }
        if (trimmed.contains("_")) {
            trimmed = trimmed.split("_")[0];
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Version string cannot be empty");
        }
        verifyVersionString(trimmed);
        String[] splitVersion = trimmed.split("\\.");
        return new Version(
            Integer.parseInt(splitVersion[0]),
            Integer.parseInt(splitVersion[1]),
            Integer.parseInt(splitVersion[2])
        );
    }
    
    private static void verifyVersionString(String version) {
        if (!VERSION_STRING.matcher(version).matches()) {
            throw new IllegalArgumentException("Invalid version string: " + version);
        }
    }
    
    @Contract (pure = true)
    @Deprecated
    public boolean isAfter(@NotNull Version other) {
        if (major != other.major) {
            return major > other.major;
        }
        if (minor != other.minor) {
            return minor > other.minor;
        }
        if (patch != other.patch) {
            return patch > other.patch;
        }
        return false;
    }
    
    @Contract (pure = true)
    public boolean isNewerThan(@NotNull Version other) {
        if (major != other.major) {
            return major > other.major;
        }
        if (minor != other.minor) {
            return minor > other.minor;
        }
        if (patch != other.patch) {
            return patch > other.patch;
        }
        return false;
    }
    
    @Contract (pure = true)
    public boolean isOlderThan(@NotNull Version other) {
        if (major != other.major) {
            return major < other.major;
        }
        if (minor != other.minor) {
            return minor < other.minor;
        }
        if (patch != other.patch) {
            return patch < other.patch;
        }
        return false;
    }
    
    @Contract (pure = true)
    @Override
    public @NotNull String toString() {
        return major + "." + minor + "." + patch;
    }
    
}

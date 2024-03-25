package ratismal.drivebackup.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class Version {
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
    public static Version parse(@NotNull String version) throws NumberFormatException {
        String[] splitVersion = version.split("\\.");
        return new Version(
            Integer.parseInt(splitVersion[0]),
            Integer.parseInt(splitVersion[1]),
            Integer.parseInt(splitVersion[2])
        );
    }
    
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

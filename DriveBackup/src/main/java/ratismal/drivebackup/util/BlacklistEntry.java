package ratismal.drivebackup.util;

import org.jetbrains.annotations.Contract;

import java.nio.file.PathMatcher;

public final class BlacklistEntry {
    private String globPattern;
    private PathMatcher pathMatcher;
    private int blacklistedFiles;

    @Contract (pure = true)
    public BlacklistEntry(String globPattern, PathMatcher pathMatcher) {
        this.globPattern = globPattern;
        this.pathMatcher = pathMatcher;
        blacklistedFiles = 0;
    }

    @Contract (mutates = "this")
    public void incBlacklistedFiles() {
        blacklistedFiles++;
    }

    @Contract (pure = true)
    public String getGlobPattern() {
        return globPattern;
    }

    @Contract (pure = true)
    public PathMatcher getPathMatcher() {
        return pathMatcher;
    }

    @Contract (pure = true)
    public int getBlacklistedFiles() {
        return blacklistedFiles;
    }
}

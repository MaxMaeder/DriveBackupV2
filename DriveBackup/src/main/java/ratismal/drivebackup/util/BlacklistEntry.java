package ratismal.drivebackup.util;

import lombok.Getter;
import org.jetbrains.annotations.Contract;

import java.nio.file.PathMatcher;

@Getter
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
    
}

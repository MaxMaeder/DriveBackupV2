package ratismal.drivebackup.util;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class SystemDependentPath {
    private static final String REGEX_FILE_SEPARATOR = File.separator.replace("\\", "\\\\"); // Escapes the backslash for the regex on Windows servers
    
    private final Path path;

    private SystemDependentPath(Path path) {
        this.path = path;
    }

    public static SystemDependentPath of(String path) throws InvalidPathException {
        if (
            (java.io.File.separator.equals("/") && path.contains("\\")) ||
            (java.io.File.separator.equals("\\") && path.contains("/"))
        ) {
            throw new InvalidPathException(path, "Path must use the correct separator, in this case, \"" + File.separator + "\"");
        }

        return new SystemDependentPath(Path.of(path));
    }

    public String toString() {
        return path.toString();
    }

    public String toStringWithSeparator(String separator) {
        return toString().replaceAll(REGEX_FILE_SEPARATOR, separator);
    }

    public String toUnixString() {
        return toStringWithSeparator("/");
    }

    public String toWindowsString() {
        return toStringWithSeparator("\\");
    }

    public String[] split() {
        return toString().split(REGEX_FILE_SEPARATOR);
    }

    public Path asPath() {
        return path;
    }
}
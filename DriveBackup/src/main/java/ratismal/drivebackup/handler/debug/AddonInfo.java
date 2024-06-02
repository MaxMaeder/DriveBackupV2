package ratismal.drivebackup.handler.debug;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class AddonInfo {
    private final AddonType type;
    private final String name;
    private final String version;
    private final List<String> authors;
    
    @Contract (pure = true)
    public AddonInfo(AddonType type, String name, String version, List<String> authors) {
        this.type = type;
        this.name = name;
        this.version = version;
        this.authors = authors;
    }
    
    @Contract (value = "_, _, _ -> new", pure = true)
    public static @NotNull AddonInfo createPluginInfo(String name, String version, List<String> authors) {
        return new AddonInfo(AddonType.PLUGIN, name, version, authors);
    }
    
    @Contract (value = "_, _, _ -> new", pure = true)
    public static @NotNull AddonInfo createModInfo(String name, String version, List<String> authors) {
        return new AddonInfo(AddonType.MOD, name, version, authors);
    }
    
    @Contract (pure = true)
    public AddonType getType() {
        return type;
    }
    
    @Contract (pure = true)
    public String getName() {
        return name;
    }
    
    @Contract (pure = true)
    public String getVersion() {
        return version;
    }
    
    @Contract (pure = true)
    public List<String> getAuthors() {
        return authors;
    }
}

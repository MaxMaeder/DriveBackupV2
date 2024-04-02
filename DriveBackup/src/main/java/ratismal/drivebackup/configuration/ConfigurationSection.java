package ratismal.drivebackup.configuration;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ConfigurationSection {
    
    private final ConfigurationObject configurationObject;
    private final String[] path;
    
    @Contract (pure = true)
    public ConfigurationSection(ConfigurationObject configurationObject, String... path) {
        this.configurationObject = configurationObject;
        this.path = path;
    }
    
    @Contract ("_ -> new")
    public @NotNull ConfigurationSection getSection(String... path) {
        List<String> list = new ArrayList<>(Arrays.asList(this.path));
        list.addAll(Arrays.asList(path));
        String[] combined = list.toArray(new String[0]);
        return new ConfigurationSection(configurationObject, combined);
    }
    
    @Contract ("_ -> new")
    public @NotNull ConfigurationValue getValue(String... path) {
        List<String> list = new ArrayList<>(Arrays.asList(this.path));
        list.addAll(Arrays.asList(path));
        String[] combined = list.toArray(new String[0]);
        return new ConfigurationValue(configurationObject, combined);
    }
}

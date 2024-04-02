package ratismal.drivebackup.configuration;

import org.jetbrains.annotations.Contract;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.List;

public final class ConfigurationValue {
    
    private final ConfigurationObject configurationObject;
    private final String[] path;
    
    @Contract (pure = true)
    public ConfigurationValue(ConfigurationObject configurationObject, String... path) {
        this.configurationObject = configurationObject;
        this.path = path;
    }
    
    public boolean getBoolean() {
        boolean defaultValue = configurationObject.getDefaults().node(path).getBoolean();
        return configurationObject.getConfig().node(path).getBoolean(defaultValue);
    }
    
    public String getString() {
        String defaultValue = configurationObject.getDefaults().node(path).getString();
        return configurationObject.getConfig().node(path).getString(defaultValue);
    }
    
    public int getInt() {
        int defaultValue = configurationObject.getDefaults().node(path).getInt();
        return configurationObject.getConfig().node(path).getInt(defaultValue);
    }
    
    public List<String> getStringList() throws SerializationException {
        List<String> defaultValue = configurationObject.getDefaults().node(path).getList(String.class);
        return configurationObject.getConfig().node(path).getList(String.class, defaultValue);
    }
}

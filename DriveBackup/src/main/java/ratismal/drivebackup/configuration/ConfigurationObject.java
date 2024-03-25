package ratismal.drivebackup.configuration;

import org.spongepowered.configurate.CommentedConfigurationNode;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.io.File;
import java.util.Objects;

/**
 * An object for holding a configuration file's information and the configuration data.
 */
public final class ConfigurationObject {
    private final File folder;
    private final String fileName;
    private String extension;
    private final DriveBackupInstance instance;
    private CommentedConfigurationNode config = CommentedConfigurationNode.root();
    private CommentedConfigurationNode defaults;
    
    public ConfigurationObject(File folder, String fileName, String extension, DriveBackupInstance instance, CommentedConfigurationNode config, CommentedConfigurationNode defaults) {
        this.folder = folder;
        this.fileName = fileName;
        this.extension = extension;
        this.config = config;
        this.instance = instance;
        this.defaults = defaults;
    }
    
    public ConfigurationObject(File folder, String fileName, String extension, DriveBackupInstance instance, CommentedConfigurationNode defaults) {
        this(folder, fileName, extension, instance, null, defaults);
    }
    
    public ConfigurationObject(File folder, String fileName, DriveBackupInstance instance, CommentedConfigurationNode defaults) {
        this(folder, fileName, null, instance, defaults);
    }
    
    public DriveBackupInstance getInstance() {
        return instance;
    }
    
    public File getFolder() {
        return folder;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public String getExtension() {
        return extension;
    }
    
    public void setExtension(String extension) {
        this.extension = extension;
    }
    
    public CommentedConfigurationNode getConfig() {
        return config;
    }
    
    public void setConfig(CommentedConfigurationNode config) {
        this.config = config;
    }
    
    public CommentedConfigurationNode getDefaults() {
        return defaults;
    }
    
    public void setDefaults(CommentedConfigurationNode defaults) {
        this.defaults = defaults;
    }
    
    public boolean isValid() {
        // folder checks
        if (folder != null && folder.exists() && folder.isDirectory()) {
            // fileName checks
            if (fileName != null && !fileName.isEmpty()) {
                // instance checks
                if (instance != null && defaults != null) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public boolean hasBeenLoaded() {
        if (config == null || extension == null || !extension.isEmpty()) {
            return false;
        }
        if (Objects.equals(config, CommentedConfigurationNode.root())) {
            return false;
        }
        return true;
    }
}

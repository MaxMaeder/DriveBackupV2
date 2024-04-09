package ratismal.drivebackup.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import com.google.common.base.Charsets;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import ratismal.drivebackup.plugin.DriveBackup;

public class CustomConfig {
    private String configName;
    private File configFile;
    private FileConfiguration config;
    
    public CustomConfig(String configName) {
        this.configName = configName;
    }

    public void reloadConfig() {
        DriveBackup instance = DriveBackup.getInstance();
        config = YamlConfiguration.loadConfiguration(configFile);
        InputStream defConfigStream = instance.getResource(configName);
        if (defConfigStream == null) {
            return;
        }
        config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, StandardCharsets.UTF_8)));
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    public void saveConfig() {
        DriveBackup instance = DriveBackup.getInstance();
        try {
            getConfig().save(configFile);
        } catch (IOException ex) {
            instance.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, ex);
        }
    }

    public void saveDefaultConfig() {
        DriveBackup instance = DriveBackup.getInstance();
        if (configFile == null) {
            configFile = new File(instance.getDataFolder(), configName);
        }
        if (!configFile.exists()) {            
            instance.saveResource(configName, false);
        }
    }
}

package ratismal.drivebackup.configuration;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import org.spongepowered.configurate.jackson.JacksonConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.xml.XmlConfigurationLoader;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public final class ConfigurationUtils {
    
    @Contract (pure = true)
    private ConfigurationUtils() {
    }
    
    /**
     * Loads the default configuration values from the given file
     * @param configObject the configuration object
     * @throws ConfigurateException if an error occurs while reading the configuration
     * @throws IllegalArgumentException if the configuration object is not valid,
     *                                  the extension is not supported,
     *                                  or a supported file could not be found in the given folder.
     */
    public static void loadConfig(@NotNull ConfigurationObject configObject) throws ConfigurateException {
        if (!configObject.isValid()) {
            throw new IllegalArgumentException("The configuration object is not valid");
        }
        String extension = configObject.getExtension();
        if (extension == null) {
            try {
            configObject.setExtension(findExtension(configObject.getFolder(), configObject.getFileName()));
            } catch (IllegalArgumentException e) {
                saveDefaultConfig(configObject.getFolder(), configObject.getFileName(), "conf", configObject.getDefaults());
            }
        }
        File file = findFile(configObject.getFolder(), configObject.getFileName(), configObject.getExtension());
        switch (configObject.getExtension()) {
            case "conf":
                configObject.setConfig(loadHocon(file));
                return;
            case "yml":
                configObject.setConfig(loadYaml(file));
                return;
            case "json":
                configObject.setConfig(loadJson(file));
                return;
            case "xml":
                configObject.setConfig(loadXml(file));
                return;
            default:
                throw new IllegalArgumentException("The extension is not supported");
        }
    }
    
    private static @NotNull File findFile(File folder, String fileName, String extension) {
        File file = new File(folder, fileName + "." + extension);
        if (!file.exists()) {
            extension = findExtension(folder, fileName);
            file = new File(folder, fileName + "." + extension);
        }
        return file;
    }
    
    private static @NotNull String findExtension(File folder, String fileName) {
        File confFile = new File(folder, fileName + ".conf");
        if (confFile.exists()) {
            return "conf";
        }
        File yamlFile = new File(folder, fileName + ".yml");
        if (yamlFile.exists()) {
            return "yml";
        }
        File jsonFile = new File(folder, fileName + ".json");
        if (jsonFile.exists()) {
            return "json";
        }
        File xmlFile = new File(folder, fileName + ".xml");
        if (xmlFile.exists()) {
            return "xml";
        }
        throw new IllegalArgumentException("Could not find a valid extension for the file name");
    }
    
    private static CommentedConfigurationNode loadHocon(@NotNull File file) throws ConfigurateException {
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().path(file.toPath()).build();
        return loader.load();
    }
    
    private static CommentedConfigurationNode loadYaml(@NotNull File file) throws ConfigurateException {
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(file.toPath()).nodeStyle(NodeStyle.BLOCK).build();
        return loader.load();
    }
    
    private static @NotNull CommentedConfigurationNode loadJson(@NotNull File file) throws ConfigurateException {
        JacksonConfigurationLoader loader = JacksonConfigurationLoader.builder().path(file.toPath()).build();
        return convertToCommented(loader.load());
    }
    
    private static @NotNull CommentedConfigurationNode loadXml(@NotNull File file) throws ConfigurateException {
        XmlConfigurationLoader loader = XmlConfigurationLoader.builder().path(file.toPath()).build();
        return convertToCommented(loader.load());
    }
    
    private static @NotNull CommentedConfigurationNode convertToCommented(ConfigurationNode node) throws SerializationException {
        if (node instanceof CommentedConfigurationNode) {
            return (CommentedConfigurationNode) node;
        }
        CommentedConfigurationNode commentedNode = CommentedConfigurationNode.root();
        for (Object key : node.childrenMap().keySet()) {
            commentedNode.node(key).set(node.node(key));
        }
        return commentedNode;
    }
    
    @Contract ("_ -> new")
    public static @NotNull ConfigurationObject generateNewConfig(
            @NotNull ConfigurationObject configObject) throws ConfigurateException {
        if (!configObject.isValid()) {
            throw new IllegalStateException("The configuration object is not valid");
        }
        renameConfig(configObject.getFolder(), configObject.getFileName(), configObject.getExtension());
        saveDefaultConfig(configObject.getFolder(), configObject.getFileName(), configObject.getExtension(), configObject.getDefaults());
        ConfigurationObject newConfigObject = new ConfigurationObject(configObject.getLogger(),
                configObject.getFolder(), configObject.getFileName(), configObject.getExtension(), configObject.getInstance(), configObject.getDefaults());
        loadConfig(newConfigObject);
        return newConfigObject;
    }
    /**
     * Rename the config file by appending “.old”, and a number up to 5
     * if more then 6 exists then it deletes the one ending in “.old6”
     * @param folder the folder where the config file is located
     * @param fileName the name of the config file
     * @param extension the extension of the config file
     * @return true if the file was renamed, false otherwise
     * @throws IllegalArgumentException if the folder is not a directory
     */
    public static boolean renameConfig(@NotNull File folder, String fileName, String extension) {
        if (!folder.isDirectory()) {
            throw new IllegalArgumentException("The folder must be a directory");
        }
        return renameFileRecursively(folder, fileName, extension);
    }
    
    private static boolean renameFileRecursively(@NotNull File folder, String fileName, String extension) {
        File[] files = folder.listFiles();
        if (files != null) {
            // Find files matching the given file name and extension
            for (File file : files) {
                if (file.isFile() && file.getName().startsWith(fileName) && file.getName().endsWith("." + extension)) {
                    // Rename the file by appending ".old", and a number
                    for (int counter = 5; counter >= 1; counter--) {
                        String newFileName = fileName + ".old" + (counter == 5 ? "" : counter) + "." + extension;
                        File newFile = new File(file.getParent(), newFileName);
                        if (newFile.exists()) {
                            // If a file with the new name already exists, rename it
                            String nextFileName = fileName + ".old" + (counter + 1) + "." + extension;
                            File nextFile = new File(file.getParent(), nextFileName);
                            if (!nextFile.exists() && newFile.renameTo(nextFile)) {
                                return file.renameTo(newFile);
                            }
                        } else {
                            // If the new name doesn't exist, rename the file to it
                            if (file.renameTo(newFile)) {
                                return true;
                            }
                        }
                    }
                    // If ".old6" exists, delete the file
                    File oldFile = new File(file.getParent(), fileName + ".old6." + extension);
                    if (oldFile.exists()) {
                        oldFile.delete();
                    }
                    break;
                }
            }
        }
        return false;
    }
    
    /**
     * Saves the default configuration values to the given file
     * After calling this method you should recreate the ConfigurationObject and call loadConfig.
     * @param folder the folder where the config file is located
     * @param fileName the name of the config file
     * @param extension the extension of the config file
     * @param defaults the default configuration values
     * @throws ConfigurateException if an error occurs while writing or generating the configuration
     * @throws IllegalArgumentException if the folder is not a directory or the extension is not supported
     * @throws IllegalStateException if the file already exists
     */
    public static void saveDefaultConfig(@NotNull File folder, String fileName, String extension, CommentedConfigurationNode defaults) throws ConfigurateException {
        if (!folder.isDirectory()) {
            throw new IllegalArgumentException("The folder must be a directory");
        }
        File file = new File(folder, fileName + "." + extension);
        if (file.exists()) {
            throw new IllegalStateException("The file already exists");
        }
        saveConfiguration(extension, defaults, file);
    }
    
    private static void saveConfiguration(@NotNull String extension, CommentedConfigurationNode defaults, File file) throws ConfigurateException {
        switch (extension) {
            case "conf":
                saveHocon(file, defaults);
                return;
            case "yml":
                saveYml(file, defaults);
                return;
            case "json":
                saveJson(file, defaults);
                return;
            case "xml":
                saveXml(file, defaults);
                return;
            default:
                throw new IllegalArgumentException("The extension is not supported");
        }
    }
    
    /**
     * saves the configuration object to the file
     * @param config the configuration object
     * @throws ConfigurateException if an error occurs while writing or generating the configuration
     * @throws IllegalArgumentException if the configuration object is not valid or the extension is not supported
     * @throws IllegalStateException if the configuration object has not been loaded or the file does not exist
     */
    public static void saveConfig(@NotNull ConfigurationObject config) throws ConfigurateException {
        if (!config.isValid()) {
            throw new IllegalArgumentException("The configuration object is not valid");
        }
        if (!config.hasBeenLoaded()) {
            throw new IllegalStateException("The configuration object has not been loaded");
        }
        File file = new File(config.getFolder(), config.getFileName() + "." + config.getExtension());
        if (!file.exists()) {
            throw new IllegalStateException("The file does not exist");
        }
        saveConfiguration(config.getExtension(), config.getConfig(), file);
    }
    
    private static void saveYml(@NotNull File file, CommentedConfigurationNode node) throws ConfigurateException {
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder().path(file.toPath()).nodeStyle(NodeStyle.BLOCK).build();
        loader.save(node);
    }
    
    private static void saveJson(@NotNull File file, CommentedConfigurationNode node) throws ConfigurateException {
        JacksonConfigurationLoader loader = JacksonConfigurationLoader.builder().path(file.toPath()).build();
        loader.save(node);
    }
    
    private static void saveXml(@NotNull File file, CommentedConfigurationNode node) throws ConfigurateException {
        XmlConfigurationLoader loader = XmlConfigurationLoader.builder().path(file.toPath()).build();
        loader.save(node);
    }
    
    private static void saveHocon(@NotNull File file, CommentedConfigurationNode node) throws ConfigurateException {
        HoconConfigurationLoader loader = HoconConfigurationLoader.builder().path(file.toPath()).emitComments(true).prettyPrinting(true).build();
        loader.save(node);
    }
    
    /**
     * Gets the value(s) from the given node
     * @param node the node
     * @return the value(s) from the node
     * @throws SerializationException if an error occurs while getting the value(s)
     */
    public static @NotNull Set<String> getVaules(@NotNull CommentedConfigurationNode node) throws SerializationException {
        Set<String> set = new HashSet<>(10);
        if (node.isList()) {
            set.addAll(node.getList(String.class));
        } else {
            set.add(node.getString());
        }
        return set;
    }
}

package ratismal.drivebackup.config;

import ratismal.drivebackup.DriveBackup;
import org.bukkit.configuration.file.FileConfiguration;

public class Config {

	DriveBackup plugin;
	FileConfiguration pluginconfig;

    /**
     * Backups
     */
    private static String dir;
    private static String format;

    /**
     * Uploading
     */
    private static String destination;

    /**
     * Messages
     */
    private static String noPerms;

	/**
	 * config constructor
	 * @param driveBackup - DriveBackup class
	 * @param pluginconfig - Plugin config
	 */
	public Config(DriveBackup driveBackup, FileConfiguration pluginconfig) {
		this.plugin = driveBackup;
		this.pluginconfig = pluginconfig;
	}

	public void reload() {
        //pluginconfig = DriveBackup.getInstance().getConfig();
        this.destination = pluginconfig.getString("destination");

        this.dir = pluginconfig.getString("dir");
        this.format = pluginconfig.getString("format");

        this.noPerms = pluginconfig.getString("no-perm");
	}

	/**
	 * Returns
	 */


    public static  String getDir() {
        return dir;
    }

    public static  String getFormat() {
        return format;
    }

    public static String getNoPerms() {
        return noPerms;
    }

    public static String getDestination() {
        return destination;
    }
}

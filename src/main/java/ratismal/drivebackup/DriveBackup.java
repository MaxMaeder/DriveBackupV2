package ratismal.drivebackup;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.handler.CommandHandler;
import ratismal.drivebackup.net.Uploader;
import ratismal.drivebackup.util.MessageUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

public class DriveBackup extends JavaPlugin {

    private Config pluginconfig;
    private static DriveBackup plugin;

    public void onEnable() {
        getConfig().options().copyDefaults(true);
        saveDefaultConfig();
        //saveResource("client_secrets.json", false);

        this.pluginconfig = new Config(this, getConfig());
        pluginconfig.reload();
        getCommand("drivebackup").setExecutor(new CommandHandler(this, pluginconfig));
        this.plugin = this;
        //Uploader.init();
    }

    public void onDisable() {
        MessageUtil.sendConsoleMessage("Stopping plugin!");
    }

    public static DriveBackup getInstance() {
        return plugin;
    }
}

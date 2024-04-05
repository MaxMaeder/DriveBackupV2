package ratismal.drivebackup.platforms.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Contract;
import ratismal.drivebackup.handler.debug.AddonInfo;
import ratismal.drivebackup.handler.debug.ServerInformation;

import java.util.ArrayList;
import java.util.List;

public class BukkitServerInformation implements ServerInformation {
    
    private final BukkitPlugin instance;
    private final Server server;
    
    @Contract (pure = true)
    public BukkitServerInformation(BukkitPlugin instance) {
        this.instance = instance;
        server = Bukkit.getServer();
    }
    
    @Override
    public String getServerType() {
        return server.getName();
    }
    
    @Override
    public String getServerVersion() {
        return server.getVersion();
    }
    
    @Override
    public boolean getOnlineMode() {
        return server.getOnlineMode();
    }
    
    @Override
    public List<AddonInfo> getAddons() {
        Plugin[] plugins = server.getPluginManager().getPlugins();
        List<AddonInfo> addons = new ArrayList<>(plugins.length);
        for (Plugin plugin : plugins) {
            addons.add(AddonInfo.createPluginInfo(plugin.getDescription().getName(), plugin.getDescription().getVersion(), plugin.getDescription().getAuthors()));
        }
        return addons;
    }
}

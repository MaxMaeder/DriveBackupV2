package ratismal.drivebackup.platforms.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Contract;
import ratismal.drivebackup.handler.debug.AddonInfo;
import ratismal.drivebackup.handler.debug.ServerInformation;

import java.util.ArrayList;
import java.util.List;

public final class BukkitServerInformation implements ServerInformation {
    
    private final BukkitPlugin instance;
    
    @Contract (pure = true)
    public BukkitServerInformation(BukkitPlugin instance) {
        this.instance = instance;
    }
    
    @Override
    public String getServerType() {
        return Bukkit.getServer().getName();
    }
    
    @Override
    public String getServerVersion() {
        return Bukkit.getServer().getVersion();
    }
    
    @Override
    public boolean getOnlineMode() {
        return Bukkit.getServer().getOnlineMode();
    }
    
    @Override
    public List<AddonInfo> getAddons() {
        Plugin[] plugins = Bukkit.getServer().getPluginManager().getPlugins();
        List<AddonInfo> addons = new ArrayList<>(plugins.length);
        for (Plugin plugin : plugins) {
            addons.add(AddonInfo.createPluginInfo(plugin.getDescription().getName(), plugin.getDescription().getVersion(), plugin.getDescription().getAuthors()));
        }
        return addons;
    }
}

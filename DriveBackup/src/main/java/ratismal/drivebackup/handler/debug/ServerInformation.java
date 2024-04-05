package ratismal.drivebackup.handler.debug;

import java.util.List;

public interface ServerInformation {
    String getServerType();
    String getServerVersion();
    boolean getOnlineMode();
    List<AddonInfo> getAddons();
    
    default String getInfo() {
        StringBuilder sb = new StringBuilder(1_000);
        sb.append("Server Type: ").append(getServerType()).append("\n");
        sb.append("Server Version: ").append(getServerVersion()).append("\n");
        sb.append("Online Mode: ").append(getOnlineMode()).append("\n");
        sb.append("Addons: \n");
        List<AddonInfo> addons = getAddons();
        int i = 0;
        for (AddonInfo addon : addons) {
            i++;
            sb.append(i).append(". ").append(addon.getName()).append(", Version").append(addon.getVersion());
            sb.append(" by ").append(String.join(", ", addon.getAuthors())).append("\n");
        }
        return sb.toString();
    }
}

package ratismal.drivebackup.platforms.bukkit.commandHandler;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.constants.Permission;
import ratismal.drivebackup.platforms.DriveBackupInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Ratismal on 2016-01-20.
 */
public class CommandTabComplete implements TabCompleter {
    
    private final DriveBackupInstance instance;
    
    public CommandTabComplete(DriveBackupInstance instance) {
        this.instance = instance;
    }
    
    public static boolean hasPerm(CommandSender player, Permission permission) {
        return player.hasPermission(permission.getPermission());
    }
    
    private boolean isMethodEnabled(String method) {
        return instance.getConfigHandler().getConfig().getSection(method).getValue("enabled").getBoolean();
    }

    /**
     * Command tab completer
     *
     * @param sender Player, who sent command
     * @param command    Command that was sent
     * @param alias  Command alias that was used
     * @param args   Arguments that followed command
     * @return String list of possible tab completions
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, @NotNull Command command, String alias, String[] args) {
        if ("drivebackup".equalsIgnoreCase(command.getName())) {
            if (args.length == 1) {
                List<String> commandList = new ArrayList<>(10);
                commandList.add("v");
                commandList.add("help");
                commandList.add("commands");
                if (hasPerm(sender, Permission.LINK_ACCOUNTS)) {
                    commandList.add("linkaccount");
                    commandList.add("unlinkaccount");
                }
                if (hasPerm(sender, Permission.RELOAD_CONFIG)) {
                    commandList.add("reloadconfig");
                    commandList.add("debug");
                }
                if (hasPerm(sender, Permission.GET_BACKUP_STATUS)) {
                    commandList.add("status");
                }
                if (hasPerm(sender, Permission.GET_NEXT_BACKUP)) {
                    commandList.add("nextbackup");
                }
                if (hasPerm(sender, Permission.BACKUP)) {
                    commandList.add("backup");
                    commandList.add("test");
                    commandList.add("update");
                }
                return commandList;
            } else if (args[0].equalsIgnoreCase("linkaccount") && args.length == 2) {
                if (!hasPerm(sender, Permission.LINK_ACCOUNTS)) {
                    return Collections.emptyList();
                }
                List<String> commandList = new ArrayList<>(3);
                commandList.add("googledrive");
                commandList.add("onedrive");
                commandList.add("dropbox");
                return commandList;
            } else if (args[0].equalsIgnoreCase("test") && args.length == 2) {
                if (!hasPerm(sender, Permission.BACKUP)) {
                    return Collections.emptyList();
                }
                List<String> commandList = new ArrayList<>(6);
                if (isMethodEnabled("googledrive")) {
                    commandList.add("googledrive");
                }
                if (isMethodEnabled("onedrive")) {
                    commandList.add("onedrive");
                }
                if (isMethodEnabled("dropbox")) {
                    commandList.add("dropbox");
                }
                if (isMethodEnabled("webdav")) {
                    commandList.add("webdav");
                }
                if (isMethodEnabled("nextcloud")) {
                    commandList.add("nextcloud");
                }
                if (isMethodEnabled("s3")) {
                    commandList.add("s3");
                }
                if (isMethodEnabled("ftp")) {
                    commandList.add("ftp");
                }
                return commandList;
            }
        }
        return Collections.emptyList();
    }
    
}

package ratismal.drivebackup.handler;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import ratismal.drivebackup.config.ConfigParser;
import ratismal.drivebackup.config.configSections.BackupMethods;
import ratismal.drivebackup.constants.Permission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Ratismal on 2016-01-20.
 */
@Deprecated
public class CommandTabComplete implements TabCompleter {
    
    public static boolean hasPerm(CommandSender player, Permission permission) {
        return player.hasPermission(permission.getPermission());
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
                if (hasPerm(sender, Permission.LINK_ACCOUNTS)) {
                    commandList.add("linkaccount");
                }
                if (hasPerm(sender, Permission.RELOAD_CONFIG)) {
                    commandList.add("reloadconfig");
                }
                if (hasPerm(sender, Permission.RELOAD_CONFIG)) {
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
                }
                if (hasPerm(sender, Permission.BACKUP)) {
                    commandList.add("test");
                }
                if (hasPerm(sender, Permission.BACKUP)) {
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
                BackupMethods methods = ConfigParser.getConfig().backupMethods;
                if (methods.googleDrive.enabled) {
                    commandList.add("googledrive");
                }
                if (methods.oneDrive.enabled) {
                    commandList.add("onedrive");
                }
                if (methods.dropbox.enabled) {
                    commandList.add("dropbox");
                }
                if (methods.webdav.enabled) {
                    commandList.add("webdav");
                }
                if (methods.nextcloud.enabled) {
                    commandList.add("nextcloud");
                }
                if (methods.s3.enabled) {
                    commandList.add("s3");
                }
                if (methods.ftp.enabled) {
                    commandList.add("ftp");
                }
                return commandList;
            }
        }
        return Collections.emptyList();
    }
}

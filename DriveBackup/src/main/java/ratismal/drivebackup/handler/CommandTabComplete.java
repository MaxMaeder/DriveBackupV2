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

public class CommandTabComplete implements TabCompleter {
    
    public static boolean hasPerm(CommandSender player, Permission permission) {
        if (player.hasPermission(permission.getPermission())) {
            return true;
        }
        return false;
    }

    /**
     * Command tab completer
     *
     * @param player Player, who sent command
     * @param cmd    Command that was sent
     * @param label  Command alias that was used
     * @param args   Arguments that followed command
     * @return String list of possible tab completions
     */
    @Override
    public List<String> onTabComplete(CommandSender player, @NotNull Command cmd, String label, String[] args) {
        if ("drivebackup".equalsIgnoreCase(cmd.getName())) {
            if (args.length == 1) {
                List<String> commandList = new ArrayList<>(10);
                commandList.add("v");
                commandList.add("help");
                if (hasPerm(player, Permission.LINK_ACCOUNTS)) {
                    commandList.add("linkaccount");
                }
                if (hasPerm(player, Permission.RELOAD_CONFIG)) {
                    commandList.add("reloadconfig");
                }
                if (hasPerm(player, Permission.RELOAD_CONFIG)) {
                    commandList.add("debug");
                }
                if (hasPerm(player, Permission.GET_BACKUP_STATUS)) {
                    commandList.add("status");
                }
                if (hasPerm(player, Permission.GET_NEXT_BACKUP)) {
                    commandList.add("nextbackup");
                }
                if (hasPerm(player, Permission.BACKUP)) {
                    commandList.add("backup");
                }
                if (hasPerm(player, Permission.BACKUP)) {
                    commandList.add("test");
                }
                if (hasPerm(player, Permission.BACKUP)) {
                    commandList.add("update");
                }
                return commandList;
            } else if (args[0].equalsIgnoreCase("linkaccount") && args.length == 2) {
                if (!hasPerm(player, Permission.LINK_ACCOUNTS)) {
                    return Collections.emptyList();
                }
                List<String> commandList = new ArrayList<>(3);
                commandList.add("googledrive");
                commandList.add("onedrive");
                commandList.add("dropbox");
                return commandList;
            } else if (args[0].equalsIgnoreCase("test") && args.length == 2) {
                if (!hasPerm(player, Permission.BACKUP)) {
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

package ratismal.drivebackup.handler;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import ratismal.drivebackup.DriveBackup;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class CommandTabComplete implements TabCompleter {

    private DriveBackup plugin;

    /**
     * CommandTabComplete constructor
     *
     * @param plugin DriveBackup plugin
     */
    public CommandTabComplete(DriveBackup plugin) {
        this.plugin = plugin;
    }

    /**
     * Command tab completer
     *
     * @param sender Player who sent command
     * @param cmd    Command that was sent
     * @param label  Command alias that was used
     * @param args   Arguments that followed command
     * @return List<String> of valid command tab options
     */
		@Override
		public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
			if (cmd.getName().equalsIgnoreCase("drivebackup")) {
				List<String> commandList = new ArrayList<>();

				if (args.length == 1) {
					commandList.add("v");
					commandList.add("help");
					commandList.add("reloadconfig");
					commandList.add("linkaccount");
					commandList.add("backup");
					commandList.add("restore");
				} else if (args[0].equalsIgnoreCase("linkaccount") && args.length == 2) {
					commandList.add("googledrive");
					commandList.add("onedrive");
				} else if (args[0].equalsIgnoreCase("restore") && args.length == 4) {
					commandList.add("closest");
					commandList.add("closestBefore");
					commandList.add("closestAfter");
				}

				return commandList;
			}
			
			return null;
		}

}

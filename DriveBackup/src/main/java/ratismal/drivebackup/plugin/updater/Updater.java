package ratismal.drivebackup.plugin.updater;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collections;

import org.apache.commons.lang.RandomStringUtils;
import org.bukkit.command.CommandSender;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ratismal.drivebackup.config.Permissions;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.util.MessageUtil;

public class Updater {
    // Plugin running Updater
    private DriveBackup plugin;
    // The plugin file (jar)
    private File file;
    // The folder that downloads will be placed in
    private File updateFolder;

    /**
     * Initialize the updater.
     *
     * @param plugin The plugin that is checking for an update.
     */
    public Updater(DriveBackup plugin, File file) {
        this.plugin = plugin;
        this.file = file;
        this.updateFolder = this.plugin.getDataFolder().getParentFile();
    }

    /**
     * Download the latest plugin jar and save it to the plugins folder.
     */
    private void downloadFile() throws FileNotFoundException, UnknownHostException, IOException  {
        String randomFilename = RandomStringUtils.randomAlphabetic(5) + ".jar";
        File outputPath = new File(this.updateFolder, randomFilename);
        
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(UpdateChecker.getLatestDownloadUrl()).build();
        Response response = client.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new IOException("Failed to download file: " + response);
        }
        FileOutputStream fos = new FileOutputStream(outputPath);
        fos.write(response.body().bytes());
        fos.close();
        outputPath.renameTo(new File(this.file.getAbsolutePath()));
    }

    public void runUpdater(CommandSender initiator) {
        if (UpdateChecker.isUpdateAvailable()) {
            if (UpdateChecker.getLatestDownloadUrl() != null) {
                try {
                    MessageUtil.sendMessage(initiator, "Attempting to download the latest version of DriveBackupV2");
                    this.downloadFile();
                    MessageUtil.sendMessageToPlayersWithPermission("Successfully updated plugin! Please restart your server in order for changes to take effect", Permissions.BACKUP, Collections.singletonList(initiator), false);
                } catch (Exception exception) {
                    MessageUtil.sendMessage(initiator, "Plugin update failed, see console for more info");
                    MessageUtil.sendConsoleException(exception);
                }
            } else {
                MessageUtil.sendMessage(initiator, "Unable to fetch latest version of DriveBackupV2");
            }
        } else {
            MessageUtil.sendMessage(initiator, "You are using the latest version of DriveBackupV2!");
        }
    }
}
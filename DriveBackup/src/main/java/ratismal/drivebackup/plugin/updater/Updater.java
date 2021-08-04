package ratismal.drivebackup.plugin.updater;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;

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
    public Updater(File file) {
        this.plugin = DriveBackup.getInstance();
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
                    MessageUtil.Builder().text("Attempting to download the latest version of DriveBackupV2").to(initiator).toConsole(false).send();
                    this.downloadFile();
                    MessageUtil.Builder().text("Successfully updated plugin! Please restart your server in order for changes to take effect").toPerm(Permissions.BACKUP).to(initiator).send();
                } catch (Exception exception) {
                    MessageUtil.Builder().text("Plugin update failed, see console for more info").to(initiator).toConsole(false).send();
                    MessageUtil.sendConsoleException(exception);
                }
            } else {
                MessageUtil.Builder().text("Unable to fetch latest version of DriveBackupV2").to(initiator).toConsole(false).send();
            }
        } else {
            MessageUtil.Builder().text("You are using the latest version of DriveBackupV2!").to(initiator).toConsole(false).send();
        }
    }
}
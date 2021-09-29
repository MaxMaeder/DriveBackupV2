package ratismal.drivebackup.plugin.updater;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;

import org.bukkit.command.CommandSender;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ratismal.drivebackup.plugin.DriveBackup;
import ratismal.drivebackup.util.Logger;
import ratismal.drivebackup.util.MessageUtil;

import static ratismal.drivebackup.config.Localization.intl;

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
        File outputPath = new File(this.updateFolder, "DriveBackupV2.jar.temp");
        
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
        Logger logger = (input, placeholders) -> {
            MessageUtil.Builder().mmText(input, placeholders).to(initiator).send();
        };

        if (UpdateChecker.isUpdateAvailable()) {
            if (UpdateChecker.getLatestDownloadUrl() != null) {
                try {
                    logger.log(intl("updater-start"));
                    downloadFile();
                    logger.log(intl("updater-successful"));
                } catch (Exception exception) {
                    logger.log(intl("updater-update-failed"));
                    MessageUtil.sendConsoleException(exception);
                }
            } else {
                logger.log(intl("updater-fetch-failed"));
            }
        } else {
            logger.log(intl("updater-no-updates"));
        }
    }
}
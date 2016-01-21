package ratismal.drivebackup.net;

import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import ratismal.drivebackup.util.FileUtil;
import ratismal.drivebackup.util.MessageUtil;

import java.io.File;

/**
 * Created by Ratismal on 2016-01-20.
 */

public class UploadThread implements Runnable {

    CommandSender sender;

    public UploadThread(CommandSender sender) {
        this.sender = sender;
    }

    @Override
    public void run() {
        File file = FileUtil.getFileToUpload(sender, false);
        try {
            MessageUtil.sendMessage(sender, "Uploading File");
            Uploader.uploadFile(file, false);
            MessageUtil.sendMessage(sender, "File Uploaded");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}

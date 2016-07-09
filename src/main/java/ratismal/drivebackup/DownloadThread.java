package ratismal.drivebackup;

import ratismal.drivebackup.ftp.FTPUploader;
import ratismal.drivebackup.googledrive.GoogleUploader;
import ratismal.drivebackup.util.MessageUtil;

import java.io.FileOutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * Created by Ratismal on 2016-07-08.
 */

public class DownloadThread implements Runnable {
    String type;
    String file;
    String service;

    public DownloadThread(String service, String type, String file) {
        this.service = service;
        this.type = type;
        this.file = file;
    }

    public void run() {
        try {
            switch (service) {
                case "googledrive":
                    GoogleUploader.downloadFile(file, type);
                    break;
                case "onedrive":
                    break;
                case "ftp":
                    FTPUploader.downloadFile(file, type);
                    break;
                default:
                    MessageUtil.sendConsoleMessage("Unknown service. Available services are: googledrive, onedrive, ftp");
                    break;
            }
        ///    URL website = new URL(url);
     //       ReadableByteChannel rbc = Channels.newChannel(website.openStream());
      //      FileOutputStream fos = new FileOutputStream("kek");
     //       fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

package ratismal.drivebackup.ftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.util.MessageUtil;

import java.io.File;
import java.io.FileInputStream;

/**
 * Created by Ratismal on 2016-03-30.
 */

public class FTPUploader {


    public static void uploadFile(File file, String type) {
        try {

            FTPClient f = new FTPClient();
            if (Config.isFtpFTPS()) {
                f = new FTPSClient();
            }
            f.connect(Config.getFtpHost(), Config.getFtpPort());
            f.login(Config.getFtpUser(), Config.getFtpPass());
            String baseDirectory = Config.getFtpDir();
            if (baseDirectory == null) {
                baseDirectory = f.printWorkingDirectory();
            }
            f.changeWorkingDirectory(baseDirectory);
            //f.changeWorkingDirectory("/");
            if (!f.changeWorkingDirectory(Config.getDestination())) {
                MessageUtil.sendConsoleMessage("Creating folder");
                f.makeDirectory(Config.getDestination());
                f.changeWorkingDirectory(Config.getDestination());
            }
            if (!f.changeWorkingDirectory(type)) {
                MessageUtil.sendConsoleMessage("Creating folder");
                f.makeDirectory(type);
                f.changeWorkingDirectory(type);
            }

            f.setFileType(FTP.BINARY_FILE_TYPE, FTP.BINARY_FILE_TYPE);
            f.setFileTransferMode(FTP.BINARY_FILE_TYPE);

            FileInputStream fs = new FileInputStream(file);
            f.storeFile(file.getName(), fs);

            MessageUtil.sendConsoleMessage(f.printWorkingDirectory());

            deleteFiles(f, type);

            f.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void deleteFiles(FTPClient f, String type) throws Exception {
        FTPFile[] files = f.listFiles();

        if (files)
    }
}

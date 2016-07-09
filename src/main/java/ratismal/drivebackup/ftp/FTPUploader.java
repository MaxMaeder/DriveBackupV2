package ratismal.drivebackup.ftp;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.util.MessageUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Ratismal on 2016-03-30.
 */

public class FTPUploader {


    public static void downloadFile(String name, String type) {
        try {
            FTPClient f = new FTPClient();
            if (Config.isFtpFTPS()) {
                f = new FTPSClient();
            }
            f.connect(Config.getFtpHost(), Config.getFtpPort());
            f.login(Config.getFtpUser(), Config.getFtpPass());
            //f.log

            File dirFile = new java.io.File("downloads/" + type);
            if (!dirFile.exists()) {
                dirFile.mkdirs();
            }
            f.changeWorkingDirectory(Config.getDestination());
            f.changeWorkingDirectory(type);
            for (FTPFile file : f.listFiles()) {
                MessageUtil.sendConsoleMessage(file.getName());
            }
            OutputStream out = new FileOutputStream("downloads/" + type + "/" + name);
            f.retrieveFile(name, out);
            MessageUtil.sendConsoleMessage("Done downloading '" + name + "' from FTP");
            out.flush();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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
            f.setListHiddenFiles(false);

            FileInputStream fs = new FileInputStream(file);
            f.storeFile(file.getName(), fs);
            fs.close();

            MessageUtil.sendConsoleMessage(f.printWorkingDirectory());

            deleteFiles(f, type);

            f.disconnect();


        } catch (Exception e) {
            if (Config.isDebug())
                e.printStackTrace();
        }
    }

    public static void deleteFiles(FTPClient f, String type) throws Exception {
        int fileLimit = Config.getKeepCount();
        if (fileLimit == -1) {
            return;
        }
        TreeMap<Date, FTPFile> files = processFiles(f);

        if (files.size() > fileLimit) {
            MessageUtil.sendConsoleMessage("There are " + files.size() + " file(s) which exceeds the " +
                    "limit of " + fileLimit + ", deleting.");
            while (files.size() > fileLimit) {
                f.deleteFile(files.firstEntry().getValue().getName());
                files.remove(files.firstKey());
            }
        }
    }

    public static TreeMap<Date, FTPFile> processFiles(FTPClient f) throws Exception {
        TreeMap<Date, FTPFile> result = new TreeMap<Date, FTPFile>();
        for (FTPFile file : f.mlistDir()) {
            if (file.getName().endsWith(".zip"))
                result.put(file.getTimestamp().getTime(), file);
        }
        return result;
    }


}

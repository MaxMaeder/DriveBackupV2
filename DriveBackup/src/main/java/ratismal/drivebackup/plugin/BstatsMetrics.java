package ratismal.drivebackup.plugin;

import java.io.IOException;
import java.util.concurrent.Callable;

import org.bstats.bukkit.*;

import ratismal.drivebackup.config.Config;
import ratismal.drivebackup.util.MessageUtil;

public class BstatsMetrics {
    public static final int METRICS_ID = 7537;

    public static void initMetrics() {
        if (Config.isMetrics()) {
            try {
                BstatsMetrics metrics = new BstatsMetrics(DriveBackup.getInstance());
                metrics.updateMetrics();

                MessageUtil.sendConsoleMessage("Metrics started");
            } catch (IOException e) {
                MessageUtil.sendConsoleMessage("Metrics failed to start");
            }
        }
    }

    private Metrics metrics;

    public BstatsMetrics(DriveBackup plugin) {
        metrics = new Metrics(plugin, METRICS_ID);
    }

    public void updateMetrics() throws IOException {
        metrics.addCustomChart(new Metrics.SimplePie("automaticBackupType", new Callable<String>() {
            @Override
            public String call() throws Exception {
                if (Config.isBackupsScheduled()) {
                    return "Schedule Based";
                } else if (Config.getBackupDelay() != -1) {
                    return "Interval Based";
                } else {
                    return "Not Enabled";
                }
            }
        }));

        metrics.addCustomChart(new Metrics.SimplePie("backupMethodEnabled", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return (Config.isGoogleDriveEnabled() || Config.isOneDriveEnabled() || Config.isFtpEnabled()) ? "Enabled" : "Disabled";
            }
        }));

        metrics.addCustomChart(new Metrics.SimplePie("googleDriveEnabled", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return Config.isGoogleDriveEnabled() ? "Enabled" : "Disabled";
            }
        }));
        
        metrics.addCustomChart(new Metrics.SimplePie("oneDriveEnabled", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return Config.isOneDriveEnabled() ? "Enabled" : "Disabled";
            }
        }));

        metrics.addCustomChart(new Metrics.SimplePie("dropboxEnabled", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return Config.isDropboxEnabled() ? "Enabled" : "Disabled";
            }
        }));

        metrics.addCustomChart(new Metrics.SimplePie("ftpEnabled", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return Config.isFtpEnabled() ? "Enabled" : "Disabled";
            }
        }));

        if (Config.isFtpEnabled()) {
            metrics.addCustomChart(new Metrics.SimplePie("sftpEnabledNew", new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return Config.isFtpSftp() ? "FTP using SSH" : "FTP";
                }
            }));
        }

        metrics.addCustomChart(new Metrics.SimplePie("updateCheckEnabled", new Callable<String>() {
            @Override
            public String call() throws Exception {
                return Config.isUpdateCheck() ? "Enabled" : "Disabled";
            }
        }));
    }
}
